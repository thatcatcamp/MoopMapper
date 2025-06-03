# MoopMapper Upload Service

FastAPI-based upload service for Burning Man lot mapping photos.

## Features

- **Photo Upload**: Accept JPEG images with metadata
- **EXIF Processing**: Extract GPS coordinates and timestamps
- **Upload Key Organization**: Organize photos by device upload keys
- **Metadata Storage**: JSON metadata for each uploaded photo
- **Web Interface**: Simple web UI for viewing uploads
- **RESTful API**: Full REST API with interactive documentation

## Quick Start

```bash
# Start the service
./start.sh

# Or manually:
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python main.py
```

## Service URLs

- **Main Interface**: http://192.168.68.104:8000
- **API Documentation**: http://192.168.68.104:8000/docs
- **Health Check**: http://192.168.68.104:8000/health

## API Endpoints

### Upload Photo
```bash
POST /upload
Content-Type: multipart/form-data

Fields:
- file: Image file (JPEG)
- upload_key: Device upload key (e.g., "Blue-Fox-Crystal")
- timestamp: Unix timestamp (optional)
- sequence: Photo sequence number (optional)
- metadata: Additional JSON metadata (optional)
```

### List Uploads
```bash
GET /uploads
# Returns all uploaded photos with metadata

GET /upload-keys  
# Returns upload keys with photo counts

GET /view/{upload_key}
# Returns all photos for specific upload key
```

## File Structure

```
upload-service/
├── uploads/                    # Uploaded photos organized by upload key
│   ├── Blue-Fox-Crystal/      # Upload key directory
│   │   ├── Blue-Fox-Crystal_1704067200_0001.jpg
│   │   └── Blue-Fox-Crystal_1704067200_0002.jpg
│   └── Red-Tiger-Mountain/
├── metadata/                   # JSON metadata for each photo
│   ├── Blue-Fox-Crystal_1704067200_0001.jpg.json
│   └── Blue-Fox-Crystal_1704067200_0002.jpg.json
└── processed/                  # Future: processed/analyzed images
```

## Example Upload

```bash
curl -X POST "http://192.168.68.104:8000/upload" \
  -F "file=@photo.jpg" \
  -F "upload_key=Blue-Fox-Crystal" \
  -F "timestamp=1704067200" \
  -F "sequence=0001"
```

## Metadata Format

Each photo gets a JSON metadata file:

```json
{
  "upload_key": "Blue-Fox-Crystal",
  "filename": "Blue-Fox-Crystal_1704067200_0001.jpg",
  "original_filename": "IMG_001.jpg",
  "timestamp": "1704067200",
  "sequence": "0001",
  "upload_time": "2024-01-01T12:00:00",
  "file_size": 2048576,
  "exif_data": {
    "GPSInfo": {
      "GPSLatitude": "40.786778",
      "GPSLongitude": "-119.203611"
    },
    "DateTime": "2024:01:01 12:00:00"
  }
}
```

## Security Notes

- **Current**: Open upload (no authentication)
- **Future**: Play Integrity API authentication
- **Network**: Runs on local network (192.168.68.104)
- **Validation**: Basic file type and size validation

## Development

The service is designed for easy extension:

1. **Authentication**: Add Play Integrity verification
2. **Processing**: Image analysis and stitching
3. **Storage**: Database integration for metadata
4. **API**: Additional endpoints for data analysis

## Monitoring

- Check service health: `GET /health`
- View logs in console output
- Monitor upload directory size
- Check metadata JSON files for integrity