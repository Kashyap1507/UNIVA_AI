# Demo guide layout: this module's code lives in Server/Karthick/models/base.py
# The file is run here, as this module, so imports and behaviour are unchanged.
import os as _os
_src = _os.path.normpath(_os.path.join(_os.path.dirname(__file__), "../../Karthick/models/base.py"))
with open(_src, encoding="utf-8") as _f:
    exec(compile(_f.read(), _src, "exec"))
