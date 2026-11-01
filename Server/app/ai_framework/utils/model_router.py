# Demo guide layout: this module's code lives in Server/Kashyap/ai_framework/utils/model_router.py
# The file is run here, as this module, so imports and behaviour are unchanged.
import os as _os
_src = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "../../../Kashyap/ai_framework/utils/model_router.py"))
with open(_src, encoding="utf-8") as _f:
    exec(compile(_f.read(), _src, "exec"))
