from . import main_v05 as v5

# v0.6 keeps the proven parser/API behaviour and aligns the backend
# version with the final concert-card Android UI release.
v5.base.VERSION = "0.6.0"
v5.base.app.version = v5.base.VERSION

app = v5.base.app
