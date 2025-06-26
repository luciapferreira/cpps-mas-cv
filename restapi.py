import io
import sys
import cv2
import numpy as np
from fastapi import FastAPI, HTTPException, UploadFile, File, Query
from fastapi.responses import JSONResponse
from ultralytics import YOLO

app = FastAPI()

# Check for manual mode flag
MANUAL_MODE = "--manual" in sys.argv

# Load YOLO model
model = YOLO("runs/detect/train/weights/best.pt")


@app.post("/inspect")
async def inspect_upload(
    station_id: str = Query(..., description="ID of the station sending this image"),
    file: UploadFile = File(...),
):
    # 1. Validate content type
    if file.content_type not in ("image/jpeg", "image/png"):
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported file type: {file.content_type}. Only JPEG or PNG is allowed."
        )

    # 2. Read the uploaded bytes
    try:
        image_bytes = await file.read()
    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Failed to read upload: {e}")

    # 3. Convert bytes to a CV2 image (BGR)
    try:
        np_arr = np.frombuffer(image_bytes, np.uint8)
        bgr_image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if bgr_image is None:
            raise ValueError("cv2.imdecode returned None")
    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Failed to decode image for CV2: {e}")

    original_image = bgr_image.copy()
    H_orig, W_orig = original_image.shape[:2]

    # 4. Deskew the white rectangle so its long side is horizontal
    gray = cv2.cvtColor(original_image, cv2.COLOR_BGR2GRAY)

    # 4.1 Threshold to isolate the white rectangle
    _, thresh = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY)

    # 4.2 Find contours, pick the largest one (assumed to be the white sheet)
    contours, _ = cv2.findContours(
        thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    if not contours:
        # If no white sheet detected, we cannot proceed with deskew logic.
        # Fall back to running YOLO on the original image only.
        deskewed = original_image.copy()
        sheet_found = False
        rect_midline = H_orig / 2  # default to image midpoint
    else:
        sheet_found = True
        largest = max(contours, key=cv2.contourArea)

        # 4.3 Fit a minimum-area rotated rectangle around that contour
        rect = cv2.minAreaRect(largest)
        (cx, cy), (_w, _h), _ = rect
        box = cv2.boxPoints(rect).astype(np.float32)  # shape: (4, 2)

        # 4.4 Find the two long edges and compute their angle
        edges = []
        for i in range(4):
            pt1 = box[i]
            pt2 = box[(i + 1) % 4]
            dx = pt2[0] - pt1[0]
            dy = pt2[1] - pt1[1]
            length = np.hypot(dx, dy)
            edges.append((length, dx, dy))

        # Sort edges by length descending
        edges_sorted = sorted(edges, key=lambda x: x[0], reverse=True)
        long_edge = edges_sorted[0]

        dx_long = long_edge[1]
        dy_long = long_edge[2]
        angle_long = np.degrees(np.arctan2(dy_long, dx_long))

        # Normalize angle_long to [-90, +90]
        if angle_long > 90:
            angle_long -= 180
        elif angle_long < -90:
            angle_long += 180

        rotation_angle = -angle_long

        # 4.5 Apply rotation to deskew
        M = cv2.getRotationMatrix2D((cx, cy), rotation_angle, 1.0)
        deskewed = cv2.warpAffine(original_image, M, (W_orig, H_orig))

        # ─────────────────────────────────────────────────────────────────────────
        # 5. In the deskewed image, re-detect the white rectangle to get its bounding box
        # ─────────────────────────────────────────────────────────────────────────
        gray_d = cv2.cvtColor(deskewed, cv2.COLOR_BGR2GRAY)
        _, thresh_d = cv2.threshold(gray_d, 200, 255, cv2.THRESH_BINARY)
        contours_d, _ = cv2.findContours(
            thresh_d, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
        )
        if not contours_d:
            # If the deskew step didn't produce a detectable sheet, fallback:
            sheet_found = False
            rect_midline = H_orig / 2
        else:
            largest_d = max(contours_d, key=cv2.contourArea)
            x_rect, y_rect, w_rect, h_rect = cv2.boundingRect(largest_d)
            # Compute the vertical midpoint of the white rectangle
            rect_midline = y_rect + h_rect / 2

    # ────────────────────────────────────────────────────────────────────────────────
    # 6. Run YOLO inference on the deskewed image and draw defect boxes
    # ────────────────────────────────────────────────────────────────────────────────
    results = model(deskewed)
    res = results[0]  # single image → single result
    has_boxes = res.boxes is not None and len(res.boxes) > 0

    annotated_rotated = deskewed.copy()
    defect_detected = False
    vertical_position = "None"

    if has_boxes:
        defect_detected = True
        # Take the first detected box (if multiple, this example uses the first)
        boxDet = res.boxes[0]
        x1, y1, x2, y2 = map(int, boxDet.xyxy[0].tolist())
        conf = float(boxDet.conf[0])
        cls = int(boxDet.cls[0])
        label = f"{model.names[cls]} {conf:.2f}" if model.names else f"{conf:.2f}"

        # Draw YOLO defect box on the rotated image
        cv2.rectangle(annotated_rotated, (x1, y1), (x2, y2), (0, 0, 255), 2)
        cv2.putText(
            annotated_rotated,
            label,
            (x1, y1 - 10),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 0, 255),
            2
        )

        if sheet_found:
            # Determine whether defect box spans upper half, bottom half, or both
            if y1 < rect_midline and y2 > rect_midline:
                vertical_position = "Both"
            elif (y1 + y2) / 2 > rect_midline:
                vertical_position = "Bottom"
            else:
                vertical_position = "Upper"
        else:
            # If no sheet was found, fall back to checking against image midpoint
            img_midline = H_orig / 2
            if y1 < img_midline and y2 > img_midline:
                vertical_position = "Both"
            elif (y1 + y2) / 2 > img_midline:
                vertical_position = "Bottom"
            else:
                vertical_position = "Upper"

    inspection_status = "NOK" if defect_detected else "OK"

    # ────────────────────────────────────────────────────────────────────────────────
    # 7. If manual mode, display original and rotated images side by side
    # ────────────────────────────────────────────────────────────────────────────────
    if MANUAL_MODE:
        display_height = 600
        scale_orig = display_height / H_orig
        scale_rot = display_height / H_orig

        orig_disp = cv2.resize(
            original_image, (int(W_orig * scale_orig), display_height)
        )
        rot_disp = cv2.resize(
            annotated_rotated, (int(W_orig * scale_rot), display_height)
        )

        combined = np.hstack((orig_disp, rot_disp))
        cv2.imshow("Original (left) vs. Rotated + YOLO (right)", combined)
        print("[Manual Mode] Press any key in the image window to continue...")
        cv2.waitKey(0)
        cv2.destroyAllWindows()

    # ────────────────────────────────────────────────────────────────────────────────
    # 8. Print result to console
    # ────────────────────────────────────────────────────────────────────────────────
    print(
        f"[Inspect] station_id={station_id}, inspection_status={inspection_status}, "
        f"defect_position={vertical_position}"
    )

    # ────────────────────────────────────────────────────────────────────────────────
    # 9. Return JSON response
    # ────────────────────────────────────────────────────────────────────────────────
    return JSONResponse(
        status_code=200,
        content={
            "station_id": station_id,
            "inspection_status": "OK",
            "defect_position": vertical_position
        },
    )


@app.get("/health")
def health_check():
    """Simple health check endpoint to verify the server is running."""
    return {"status": "alive"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("restapi:app", host="127.0.0.1", port=8000, log_level="info")
