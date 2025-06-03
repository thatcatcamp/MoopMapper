from fastapi import FastAPI, File, UploadFile, Form, HTTPException, status
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import shutil
import os
import json
from datetime import datetime
from pathlib import Path
from typing import Optional
import uuid
from PIL import Image
from PIL.ExifTags import TAGS, GPSTAGS
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="MoopMapper Upload Service",
    description="FastAPI service for uploading and managing Burning Man lot mapping photos",
    version="1.0.0"
)

# Configure CORS for local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify actual origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Create directories
UPLOAD_DIR = Path("uploads")
PROCESSED_DIR = Path("processed")
METADATA_DIR = Path("metadata")

for directory in [UPLOAD_DIR, PROCESSED_DIR, METADATA_DIR]:
    directory.mkdir(exist_ok=True)

# Serve static files for web interface
app.mount("/static", StaticFiles(directory="static"), name="static")

@app.get("/", response_class=HTMLResponse)
async def read_root():
    """Serve a simple web interface for viewing uploads"""
    return """
    <html>
        <head>
            <title>MoopMapper Upload Service</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 40px; }
                .header { background: #2196F3; color: white; padding: 20px; border-radius: 8px; }
                .stats { background: #f5f5f5; padding: 20px; margin: 20px 0; border-radius: 8px; }
                .upload-keys { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin-top: 20px; }
                .key-card { background: white; border: 1px solid #ddd; padding: 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>🗺️ MoopMapper Upload Service</h1>
                <p>Running on 192.168.68.104 - Burning Man Lot Mapping Platform</p>
            </div>
            
            <div class="stats">
                <h2>Service Status</h2>
                <p>✅ Upload service is running</p>
                <p>📁 Upload directory: ./uploads</p>
                <p>🔧 Authentication: Open (Play Integrity coming soon)</p>
            </div>
            
            <div>
                <h2>API Endpoints</h2>
                <ul>
                    <li><strong>POST /upload</strong> - Upload photos with metadata</li>
                    <li><strong>GET /uploads</strong> - List all uploads</li>
                    <li><strong>GET /upload-keys</strong> - List upload keys</li>
                    <li><strong>GET /view/{upload_key}</strong> - View uploads by key</li>
                    <li><strong>GET /docs</strong> - Interactive API documentation</li>
                </ul>
            </div>
            
            <div>
                <h2>Recent Uploads</h2>
                <p><a href="/uploads">View all uploads →</a></p>
                <p><a href="/upload-keys">View by upload key →</a></p>
            </div>
        </body>
    </html>
    """

@app.post("/upload")
async def upload_photo(
    file: UploadFile = File(...),
    upload_key: str = Form(...),
    timestamp: Optional[str] = Form(None),
    sequence: Optional[str] = Form(None),
    metadata: Optional[str] = Form(None)
):
    """
    Upload a photo with associated metadata
    """
    try:
        # Validate file type
        if not file.content_type.startswith('image/'):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="File must be an image"
            )
        
        # Generate unique filename with original structure
        timestamp_str = timestamp or str(int(datetime.now().timestamp()))
        sequence_str = sequence or "0001"
        
        # Create upload key directory
        upload_key_dir = UPLOAD_DIR / upload_key
        upload_key_dir.mkdir(exist_ok=True)
        
        # Save file with original naming convention
        filename = f"{upload_key}_{timestamp_str}_{sequence_str}.jpg"
        file_path = upload_key_dir / filename
        
        # Save uploaded file
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        
        # Extract EXIF data
        exif_data = extract_exif_data(file_path)
        
        # Create metadata record
        metadata_record = {
            "upload_key": upload_key,
            "filename": filename,
            "original_filename": file.filename,
            "timestamp": timestamp_str,
            "sequence": sequence_str,
            "upload_time": datetime.now().isoformat(),
            "file_size": file_path.stat().st_size,
            "exif_data": exif_data,
            "additional_metadata": json.loads(metadata) if metadata else {}
        }
        
        # Save metadata
        metadata_file = METADATA_DIR / f"{filename}.json"
        with open(metadata_file, "w") as f:
            json.dump(metadata_record, f, indent=2)
        
        logger.info(f"Uploaded photo: {filename} for key: {upload_key}")
        
        return {
            "status": "success",
            "filename": filename,
            "upload_key": upload_key,
            "message": "Photo uploaded successfully",
            "metadata": metadata_record
        }
        
    except Exception as e:
        logger.error(f"Upload failed: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Upload failed: {str(e)}"
        )

@app.get("/uploads")
async def list_uploads():
    """List all uploaded files with metadata"""
    uploads = []
    
    for metadata_file in METADATA_DIR.glob("*.json"):
        try:
            with open(metadata_file, "r") as f:
                metadata = json.load(f)
                uploads.append(metadata)
        except Exception as e:
            logger.warning(f"Failed to read metadata file {metadata_file}: {e}")
    
    # Sort by upload time, newest first
    uploads.sort(key=lambda x: x.get("upload_time", ""), reverse=True)
    
    return {
        "total_uploads": len(uploads),
        "uploads": uploads
    }

@app.get("/upload-keys")
async def list_upload_keys():
    """List all unique upload keys with photo counts"""
    upload_keys = {}
    
    for upload_dir in UPLOAD_DIR.iterdir():
        if upload_dir.is_dir():
            photo_count = len(list(upload_dir.glob("*.jpg")))
            upload_keys[upload_dir.name] = {
                "upload_key": upload_dir.name,
                "photo_count": photo_count,
                "directory": str(upload_dir)
            }
    
    return {
        "total_keys": len(upload_keys),
        "upload_keys": upload_keys
    }

@app.get("/view/{upload_key}")
async def view_upload_key(upload_key: str):
    """View all uploads for a specific upload key"""
    uploads = []
    
    # Find all metadata files for this upload key
    for metadata_file in METADATA_DIR.glob(f"{upload_key}_*.json"):
        try:
            with open(metadata_file, "r") as f:
                metadata = json.load(f)
                if metadata.get("upload_key") == upload_key:
                    uploads.append(metadata)
        except Exception as e:
            logger.warning(f"Failed to read metadata file {metadata_file}: {e}")
    
    if not uploads:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No uploads found for upload key: {upload_key}"
        )
    
    # Sort by sequence number
    uploads.sort(key=lambda x: x.get("sequence", "0000"))
    
    return {
        "upload_key": upload_key,
        "total_photos": len(uploads),
        "uploads": uploads
    }

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "service": "MoopMapper Upload Service",
        "timestamp": datetime.now().isoformat(),
        "version": "1.0.0"
    }

def extract_exif_data(image_path: Path) -> dict:
    """Extract EXIF data from image file"""
    try:
        image = Image.open(image_path)
        exif_dict = image._getexif()
        
        if exif_dict is None:
            return {}
        
        exif = {}
        gps_info = {}
        
        for tag_id, value in exif_dict.items():
            tag = TAGS.get(tag_id, tag_id)
            
            if tag == "GPSInfo":
                for gps_tag_id, gps_value in value.items():
                    gps_tag = GPSTAGS.get(gps_tag_id, gps_tag_id)
                    gps_info[gps_tag] = gps_value
            else:
                exif[tag] = str(value)
        
        if gps_info:
            exif["GPSInfo"] = gps_info
        
        return exif
        
    except Exception as e:
        logger.warning(f"Failed to extract EXIF data from {image_path}: {e}")
        return {}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="192.168.68.104",
        port=8000,
        reload=True,
        log_level="info"
    )