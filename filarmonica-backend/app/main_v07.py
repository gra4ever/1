from . import main_v06 as v6

# v0.7 keeps the proven parser/API behaviour and aligns the backend
# version with the approved concert-card Android UI release.
v6.v5.base.VERSION = "0.7.0"
v6.v5.base.app.version = v6.v5.base.VERSION

app = v6.v5.base.app
