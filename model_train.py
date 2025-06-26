import os
import shutil
import random
import time
#from torch.cuda import device
from ultralytics import YOLO

# Input
FOLDER_PATH = input("Input folder path (relative or absolute): ").strip()
if not FOLDER_PATH:
    print("ERROR!!! No path specified!")
    exit(0)
LABELS=str(input("Input labels, seperate by spaces (default: defects): ") or "defects")

# Configuration
IMAGES_DIR = f"{FOLDER_PATH}/images"
LABELS_DIR = f"{FOLDER_PATH}/labels"
OUTPUT_DIR = f"{FOLDER_PATH}_split"
SPLITS = {"train": 0.70, "val": 0.20, "test": 0.10}

#Yolo Configuration
MODEL="yolo11n.yaml"
TRAIN_CONFIG = {
    "epochs": 100,
    "imgsz": 640,
    "device": "mps"
}
YAML_CONTENTS=f"""
train: ./train/images
val: ./val/images
test: ./test/images

nc: 1
names: ['{LABELS}']
"""

if [os.path.exists(OUTPUT_DIR)]:
    shutil.rmtree(OUTPUT_DIR) # Recreate OUTPUT_DIR
    time.sleep(10)

# Create output directories
for split in SPLITS:
    os.makedirs(os.path.join(OUTPUT_DIR, split, "images"), exist_ok=True)
    os.makedirs(os.path.join(OUTPUT_DIR, split, "labels"), exist_ok=True)

# Match images and labels
image_files = [f for f in os.listdir(IMAGES_DIR) if f.endswith(('.jpg', '.png', '.jpeg'))]
random.shuffle(image_files)

total = len(image_files)
train_end = int(total * SPLITS["train"])
val_end = train_end + int(total * SPLITS["val"])

splits = {
    "train": image_files[:train_end],
    "val": image_files[train_end:val_end],
    "test": image_files[val_end:]
}

# Copy files
for split, files in splits.items():
    for img_file in files:
        base = os.path.splitext(img_file)[0]
        label_file = base + ".txt"

        img_src = os.path.join(IMAGES_DIR, img_file)
        lbl_src = os.path.join(LABELS_DIR, label_file)

        img_dst = os.path.join(OUTPUT_DIR, split, "images", img_file)
        lbl_dst = os.path.join(OUTPUT_DIR, split, "labels", label_file)

        if os.path.exists(img_src):
            shutil.copyfile(img_src, img_dst)
        if os.path.exists(lbl_src):
            shutil.copyfile(lbl_src, lbl_dst)

with open(f"{OUTPUT_DIR}/dataset_custom.yaml", 'w') as f:
    f.write(YAML_CONTENTS)

print(f"✅ Dataset split complete! Output in '{OUTPUT_DIR}'")

try:
    print(f"⚠️ The program will train the model in 5 seconds (ctr+c to stop)")
    time.sleep(5)
    model = YOLO(MODEL)
    results = model.train(data=os.path.abspath(os.path.join(OUTPUT_DIR, "dataset_custom.yaml")), **TRAIN_CONFIG)

except KeyboardInterrupt:
    print("Stoping the program")