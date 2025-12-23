import os
from PIL import Image

# Configuration
SOURCE_IMAGE = r"C:/Users/Bob/.gemini/antigravity/brain/b5543c58-8996-4efb-b949-fa34f54216a9/uploaded_image_1766472487645.jpg"
RES_DIR = r"d:/DEV_DATA/mesh-client/app/src/main/res"

# Icon definition (density_name: size_px)
ICON_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive icon foreground definition (density_name: size_px) (standard 108dp * density)
FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

def process_icons():
    print(f"Opening source image: {SOURCE_IMAGE}")
    try:
        img = Image.open(SOURCE_IMAGE)
    except Exception as e:
        print(f"Error opening image: {e}")
        return

    # 1. Generate Legacy Launcher Icons (ic_launcher.png)
    for folder, size in ICON_SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        
        # Resize using LANCZOS for high quality
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        
        out_path = os.path.join(out_dir, "ic_launcher.png")
        resized.save(out_path, "PNG")
        print(f"Saved {out_path} ({size}x{size})")
        
        # Also save as ic_launcher_round.png for round icon support
        out_path_round = os.path.join(out_dir, "ic_launcher_round.png")
        resized.save(out_path_round, "PNG")
        print(f"Saved {out_path_round} ({size}x{size})")

    # 2. Generate Adaptive Foreground Icons (ic_launcher_foreground.png)
    # Ideally, we want the logo to fit within the safe zone (66px diameter in 108px canvas).
    # Since our source is a full-bleed square/squircle, we'll scale it down slightly 
    # and center it on a transparent background to ensure it's not clipped.
    
    for folder, size in FOREGROUND_SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        
        # Create a transparent canvas
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        
        # Scale the image to ~70% of the canvas size to be safe
        target_size = int(size * 0.7) 
        resized = img.resize((target_size, target_size), Image.Resampling.LANCZOS)
        
        # Center the image
        x = (size - target_size) // 2
        y = (size - target_size) // 2
        
        canvas.paste(resized, (x, y))
        
        out_path = os.path.join(out_dir, "ic_launcher_foreground.png")
        canvas.save(out_path, "PNG")
        print(f"Saved {out_path} ({size}x{size} [content: {target_size}x{target_size}])")

    # 3. Generate Splash Logo
    splash_dir = os.path.join(RES_DIR, "drawable")
    os.makedirs(splash_dir, exist_ok=True)
    
    # Save a high-res version for splash
    splash_size = 512
    splash_img = img.resize((splash_size, splash_size), Image.Resampling.LANCZOS)
    splash_path = os.path.join(splash_dir, "splash_logo.png")
    splash_img.save(splash_path, "PNG")
    print(f"Saved {splash_path} ({splash_size}x{splash_size})")

    # 4. Ensure background color XML exists
    values_dir = os.path.join(RES_DIR, "values")
    os.makedirs(values_dir, exist_ok=True)
    colors_xml = os.path.join(values_dir, "ic_launcher_background.xml")
    
    # We'll just define the color in the colors.xml or specific launcher background xml
    # Actually, the adaptive icon XML references @drawable/ic_launcher_background
    # which can be a color file or a drawable.
    # Let's check if we can create a simple XML for it.
    
    background_xml_content = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#1A1A1A</color>
</resources>"""
    
    # We usually put this in values/ic_launcher_background.xml or similar.
    # But wait, existing ic_launcher.xml references @drawable/ic_launcher_background
    # If it is a color, it should be @color/ic_launcher_background or the file should be in drawable/
    
    # Let's create a drawable that is just a color
    drawable_dir = os.path.join(RES_DIR, "drawable")
    bg_drawable_path = os.path.join(drawable_dir, "ic_launcher_background.xml")
    
    with open(bg_drawable_path, "w") as f:
        f.write("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#1A1A1A"
        android:pathData="M0,0h108v108h-108z"/>
</vector>""")
    print(f"Created {bg_drawable_path}")

if __name__ == "__main__":
    process_icons()
