import sys
import os
import zlib
import base64
import urllib.request

def plantuml_encode(plantuml_text):
    # 1. UTF-8 encode
    utf8_str = plantuml_text.encode('utf-8')
    # 2. Compress using deflate (zlib without headers/footers)
    # We use zlib.compressobj to get raw deflate
    compressor = zlib.compressobj(zlib.Z_DEFAULT_COMPRESSION, zlib.DEFLATED, -15)
    compressed = compressor.compress(utf8_str)
    compressed += compressor.flush()
    
    # 3. Custom Base64 encoding
    b64_encoded = base64.b64encode(compressed).decode('utf-8')
    
    # Translate standard base64 alphabet to PlantUML's alphabet
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    plantuml_alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
    trans = str.maketrans(alphabet, plantuml_alphabet)
    
    return b64_encoded.translate(trans).replace('=', '')

def render_file(puml_filepath, png_filepath):
    print(f"Reading {puml_filepath}...")
    with open(puml_filepath, "r", encoding="utf-8") as f:
        text = f.read()
    
    encoded = plantuml_encode(text)
    url = f"http://www.plantuml.com/plantuml/png/{encoded}"
    
    print(f"Downloading from {url}...")
    try:
        req = urllib.request.Request(
            url, 
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        )
        with urllib.request.urlopen(req) as response:
            with open(png_filepath, "wb") as f_out:
                f_out.write(response.read())
        print(f"Saved rendered image to {png_filepath}")
        return True
    except Exception as e:
        print(f"Error rendering diagram: {e}")
        return False

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python render_puml.py <input.puml> <output.png>")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    success = render_file(input_file, output_file)
    if not success:
        sys.exit(1)
