"""Процедурные текстуры мода «Простор» (Pillow). Запуск: python3 tools/textures.py из папки prostor-mod."""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "prostor", "textures")

# палитра камыша: стебли и листья приглушённо-зелёные, как у ванильного сахарного тростника и травы,
# початки - тёплые коричневые
DARK = (62, 92, 38, 255)
MID = (88, 124, 50, 255)
LIGHT = (122, 156, 68, 255)
TIP = (150, 170, 92, 255)
HEAD_DARK = (92, 56, 30, 255)
HEAD = (122, 76, 40, 255)
HEAD_LIGHT = (150, 98, 56, 255)


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def stem(img, x0, y_top, y_bottom, lean, rnd):
    """Стебель от y_bottom вверх до y_top с лёгким наклоном lean (сдвиг x за всю высоту)."""
    h = y_bottom - y_top
    for i in range(h + 1):
        y = y_bottom - i
        x = x0 + round(lean * i / max(h, 1))
        put(img, x, y, MID if (i + x0) % 3 else DARK)
        if rnd.random() < 0.25:
            put(img, x, y, LIGHT)


def leaf(img, x0, y0, length, direction, rnd):
    """Узкий лист, отходящий от стебля вверх и вбок (direction = -1 влево, +1 вправо)."""
    x, y = x0, y0
    for i in range(length):
        y -= 1
        if i % 2 == 1:
            x += direction
        put(img, x, y, LIGHT if i < length - 1 else TIP)
        if i < length // 2:
            put(img, x - direction, y + 1, MID)


def reeds_bottom(seed=11):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x0, lean in ((3, 1), (7, 0), (10, -1), (13, 0)):
        stem(img, x0, 0, 15, lean, rnd)
    leaf(img, 4, 12, 9, -1, rnd)
    leaf(img, 10, 13, 10, 1, rnd)
    leaf(img, 7, 9, 7, 1, rnd)
    leaf(img, 13, 10, 6, -1, rnd)
    return img


def reeds_top(seed=12):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # стебли продолжаются снизу (наклоны совпадают с нижней половиной на стыке)
    stem(img, 4, 7, 15, 0, rnd)
    stem(img, 9, 4, 15, 0, rnd)
    stem(img, 13, 9, 15, 0, rnd)
    # два початка рогоза: 2 пикселя шириной, с бликом слева и тенью снизу
    for hx, hy, hh in ((3, 3, 5), (8, 1, 4)):
        for y in range(hy, hy + hh):
            put(img, hx, y, HEAD_LIGHT if y < hy + hh - 1 else HEAD)
            put(img, hx + 1, y, HEAD if y < hy + hh - 1 else HEAD_DARK)
        put(img, hx + 1, hy - 1, TIP)
        put(img, hx + 1, hy - 2, TIP)
    leaf(img, 13, 12, 9, 1, rnd)
    leaf(img, 6, 14, 10, -1, rnd)
    leaf(img, 11, 13, 7, -1, rnd)
    return img


def main():
    block = os.path.join(OUT, "block")
    item = os.path.join(OUT, "item")
    os.makedirs(block, exist_ok=True)
    os.makedirs(item, exist_ok=True)
    bottom, top = reeds_bottom(), reeds_top()
    bottom.save(os.path.join(block, "reeds_bottom.png"))
    top.save(os.path.join(block, "reeds_top.png"))
    top.save(os.path.join(item, "reeds.png"))


if __name__ == "__main__":
    main()
