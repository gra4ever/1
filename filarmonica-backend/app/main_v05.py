from . import main_v04 as base

# v0.5 keeps the proven v0.4 parser/API behaviour and aligns the backend
# version with the Android v0.5 client release.
base.VERSION = "0.5.0"
base.app.version = base.VERSION

app = base.app
