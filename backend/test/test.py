role_voices = {
    "woman":     "cosyvoice-v3.5-flash-uservoice-8d7f710c721841a4af74e11203a03a42",
    "man":       "cosyvoice-v3.5-flash-uservoice-b501e8f077504c9e9ffb87c0604d0857",
    "old_woman": "cosyvoice-v3.5-flash-uservoice-0367a656904649e6b7ffd08d472a0760",
    "old_man":   "cosyvoice-v3.5-flash-uservoice-45ca70fb83d24b25aa20cc211045d5ac",
    "child_girl":"cosyvoice-v3.5-flash-uservoice-73a12ed62eb346ee9da88ff07fb4ca12",
    "child_boy": "cosyvoice-v3.5-flash-uservoice-b1bfa17c4ef2406299f84842e57c2cef",
}

def test(voice_role: str):
    r = role_voices[voice_role]
    print(r)

def test1(vioce_id: str):
    r =not vioce_id in role_voices.values()
    print(r)

test1("cosyvoice-v3.5-flash-uservoice-73a12ed62eb346ee9da88ff07fb4ca12")