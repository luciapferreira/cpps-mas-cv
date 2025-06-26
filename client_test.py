import os
import random
import sys
import requests

def send_random_image(server_url: str, station_id: str, image_folder: str):
    """
    Picks a random image from the specified folder and sends it to the /inspect endpoint.

    :param server_url: Base URL of the FastAPI server (e.g. http://127.0.0.1:8000)
    :param station_id: Station ID to include in the request (e.g. "1")
    :param image_folder: Path to the folder containing images
    """
    # 1. Gather all JPEG/PNG images in the folder
    try:
        all_images = [
            f for f in os.listdir(image_folder)
            if f.lower().endswith((".jpg", ".jpeg", ".png"))
        ]
    except Exception as e:
        print(f"[Error] Could not list directory '{image_folder}': {e}")
        sys.exit(1)

    if not all_images:
        print(f"[Error] No images found in folder '{image_folder}'.")
        sys.exit(1)

    # 2. Pick a random image file
    image_file = random.choice(all_images)
    image_path = os.path.join(image_folder, image_file)
    print(f"Selected image: {image_file}")

    # 3. Prepare and send the POST request
    endpoint = f"{server_url.rstrip('/')}/inspect"
    params = {"station_id": station_id}

    try:
        with open(image_path, "rb") as img_file:
            # guessing JPEG; if PNG, requests will still set a generic content-type
            files = {"file": (image_file, img_file, "image/jpeg")}
            response = requests.post(endpoint, params=params, files=files)
    except Exception as e:
        print(f"[Error] Failed to open or send image: {e}")
        sys.exit(1)

    # 4. Handle response
    if response.status_code != 200:
        print(f"[Error] Server returned status {response.status_code}: {response.text}")
        sys.exit(1)

    try:
        data = response.json()
    except ValueError:
        print(f"[Error] Server did not return valid JSON: {response.text}")
        sys.exit(1)

    print("Response from server:")
    for key, val in data.items():
        print(f"  {key}: {val}")


if __name__ == "__main__":
    # You can modify these paths/URLs as needed:
    SERVER_URL = "http://127.0.0.1:8000"
    STATION_ID = "1"
    IMAGE_FOLDER = "dataset/images"

    send_random_image(SERVER_URL, STATION_ID, IMAGE_FOLDER)
