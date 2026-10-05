"""Export the rectangular signal tube's textures, Java model and Blockbench project.
The atlas is original imagegen source art; slicing/resampling is reproducible asset export.
"""
from pathlib import Path
import base64
import json
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/airdrop_supply_drops'


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def main():
    atlas = Image.open(ROOT / 'art/source/signal-tube-atlas.png').convert('RGBA')
    w, h = atlas.size
    names = ['body', 'metal', 'dark', 'label']
    rectangles = [(0, 0, w//2, h//2), (w//2, 0, w, h//2), (0, h//2, w//2, h), (w//2, h//2, w, h)]
    textures = {}
    for name, rect in zip(names, rectangles):
        output = ASSETS / f'textures/item/signal_{name}.png'
        output.parent.mkdir(parents=True, exist_ok=True)
        # Preserve the artwork; resize/crop only to export its predesigned atlas cells.
        atlas.crop(rect).resize((128, 128) if name in ['body', 'label'] else (64, 64), Image.Resampling.LANCZOS).save(output)
        textures[name] = f'airdrop_supply_drops:item/signal_{name}'
    textures['particle'] = textures['body']
    model = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': [], 'display': {
        'gui': {'rotation': [25, -35, 0], 'translation': [0, 0, 0], 'scale': [1.1, 1.1, 1.1]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 0, 0], 'scale': [1, 1, 1]},
        # Minecraft mirrors left-hand X offsets itself. Keep the local tube axis upright.
        'firstperson_righthand': {'rotation': [0, -15, 0], 'translation': [1.25, 6, -2], 'scale': [.85, .85, .85]},
        'firstperson_lefthand': {'rotation': [0, -15, 0], 'translation': [1.25, 6, -2], 'scale': [.85, .85, .85]},
        # The humanoid hand layer already rotates -90 X and 180 Y before item transforms.
        # +90 X keeps the mouth above the grip; Z translation aligns the lower sleeve with the hand.
        'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 3, 5], 'scale': [.85, .85, .85]},
        'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 3, 5], 'scale': [.85, .85, .85]}}}
    def box(name, start, end, material):
        faces = {f: {'uv': [0, 0, 16, 16], 'texture': '#' + material}
                 for f in ['north', 'south', 'east', 'west', 'up', 'down']}
        model['elements'].append({'name': name, 'from': start, 'to': end, 'faces': faces})
        return faces
    body = box('olive_rectangular_tube', [7.05, 3.8, 7.05], [8.95, 14.2, 8.95], 'body')
    body['north'] = {'uv': [0, 0, 16, 16], 'texture': '#label', 'rotation': 90}
    body['up']['texture'] = '#dark'
    box('mouth_front', [7.05, 14.2, 7.05], [8.95, 15.5, 7.35], 'body')
    box('mouth_back', [7.05, 14.2, 8.65], [8.95, 15.5, 8.95], 'body')
    box('mouth_left', [7.05, 14.2, 7.35], [7.35, 15.5, 8.65], 'body')
    box('mouth_right', [8.65, 14.2, 7.35], [8.95, 15.5, 8.65], 'body')
    box('metal_sleeve', [6.92, 1.9, 6.92], [9.08, 4.2, 9.08], 'metal')
    box('grip_core', [6.95, .3, 6.95], [9.05, 1.9, 9.05], 'metal')
    for i in range(5):
        y = .35 + i * .30
        box(f'square_grip_rib_{i}', [6.83, y, 6.83], [9.17, y+.18, 9.17], 'metal')
    box('base_cap', [6.88, .1, 6.88], [9.12, .3, 9.12], 'dark')
    write_json(ASSETS / 'models/item/signal_tube.json', model)
    bbtextures = []
    for index, name in enumerate(names):
        png = ASSETS / f'textures/item/signal_{name}.png'
        size = Image.open(png).size[0]
        bbtextures.append({'name': png.name, 'namespace': 'airdrop_supply_drops', 'folder': 'item', 'id': str(index),
            'width': size, 'height': size, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, f'airdrop/signal/{name}')),
            'source': 'data:image/png;base64,' + base64.b64encode(png.read_bytes()).decode()})
    elements = []
    for cube in model['elements']:
        faces = {face: {**{key: value for key, value in spec.items() if key != 'texture'},
                       'texture': names.index(spec['texture'][1:])} for face, spec in cube['faces'].items()}
        elements.append({'name': cube['name'], 'type': 'cube', 'from': cube['from'], 'to': cube['to'],
            'origin': [8, 8, 8], 'box_uv': False, 'faces': faces,
            'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, f'airdrop/signal/{cube["name"]}'))})
    project = {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
               'name': 'signal_tube', 'model_identifier': 'signal_tube', 'resolution': {'width': 16, 'height': 16},
               'elements': elements, 'outliner': [e['uuid'] for e in elements], 'textures': bbtextures, 'display': model['display']}
    write_json(ROOT / 'art/source/signal_tube.bbmodel', project)
    print(f'Exported rectangular signal tube: {len(elements)} cubes, four textures, embedded-texture Blockbench project')


if __name__ == '__main__': main()
