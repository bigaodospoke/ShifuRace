// Gera as texturas, o modelo 3D do troféu de corrida e o ícone do mod, desenhados por código (sem dependências).
//   node tools/gerar_recursos.js
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const ASSETS = path.join(__dirname, "..", "src", "main", "resources", "assets", "shifurace");

// ---------------------------------------------------------------- PNG

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

/** Imagem RGBA: px(x, y) = [r, g, b, a] */
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const [r, g, b, a = 255] = px(x, y);
      const o = y * (w * 4 + 1) + 1 + x * 4;
      raw[o] = r; raw[o + 1] = g; raw[o + 2] = b; raw[o + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

function save(rel, buf) {
  const file = path.join(ASSETS, rel);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, buf);
  console.log("+", path.relative(path.join(__dirname, ".."), file));
}

const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const gray = (v) => [clamp(v), clamp(v), clamp(v)];

// ---------------------------------------------------------------- texturas do troféu

// Taça em tons de cinza claro: o jogo pinta com a cor da pista (tintindex 0)
const CUP_SHADE = [150, 168, 186, 202, 214, 226, 238, 252, 255, 244, 230, 214, 198, 182, 166, 150];
save("textures/block/trofeu_corrida_taca.png", png(16, 16, (x, y) => {
  let v = CUP_SHADE[x] - (y % 4 === 3 ? 10 : 0);
  if (x === 7 || x === 8) v += y < 8 ? 6 : 0;
  return gray(v);
}));

// Base escura (obsidiana) com filete claro nas bordas
save("textures/block/trofeu_corrida_base.png", png(16, 16, (x, y) => {
  const edge = x === 0 || y === 0 || x === 15 || y === 15;
  const n = ((x * 7 + y * 13) % 5) * 3;
  return edge ? [78, 70, 92] : [38 + n, 33 + n, 46 + n];
}));

// Plaquinha dourada com "texto" gravado
save("textures/block/trofeu_corrida_placa.png", png(16, 16, (x, y) => {
  const edge = x === 0 || y === 0 || x === 15 || y === 15;
  if (edge) return [168, 117, 18];
  if ((y === 5 || y === 9) && x > 2 && x < 13 && x % 3 !== 0) return [120, 80, 10];
  return [245, 197 + (y < 4 ? 20 : 0), 66];
}));

// Bandeira quadriculada (quadrados de 4 px)
save("textures/block/trofeu_corrida_bandeira.png", png(16, 16, (x, y) => {
  const white = (Math.floor(x / 4) + Math.floor(y / 4)) % 2 === 0;
  return white ? [240, 240, 240] : [24, 24, 28];
}));

// Mastro prateado
save("textures/block/trofeu_corrida_mastro.png", png(16, 16, (x) => gray([170, 210, 240, 200][x % 4])));

// ---------------------------------------------------------------- ícone do mod (64x64)

save("icon.png", png(64, 64, (x, y) => {
  const inside = x >= 2 && y >= 2 && x < 62 && y < 62;
  if (!inside) return [0, 0, 0, 0];
  const border = x < 5 || y < 5 || x >= 59 || y >= 59;
  if (border) return [225, 6, 0];
  // Bandeira quadriculada ondulando no alto
  const fx = x - 14, fy = y - 10 - Math.round(Math.sin(x / 6) * 2);
  if (fx >= 0 && fx < 36 && fy >= 0 && fy < 20) {
    return (Math.floor(fx / 6) + Math.floor(fy / 6)) % 2 === 0 ? [245, 245, 245] : [20, 20, 24];
  }
  // Mastro
  if (x >= 11 && x < 14 && y >= 8 && y < 56) return [200, 200, 210];
  // Taça dourada embaixo
  const cx = 38;
  if (y >= 34 && y < 46 && Math.abs(x - cx) <= 10 - (y - 34) / 2) return [245, 197, 66];
  if (y >= 46 && y < 50 && Math.abs(x - cx) <= 2) return [210, 160, 40];
  if (y >= 50 && y < 54 && Math.abs(x - cx) <= 7) return [60, 52, 70];
  return [24, 22, 30];
}));

// ---------------------------------------------------------------- modelo do troféu

function element(name, from, to, texture, tint, faces = ["north", "south", "east", "west", "up", "down"]) {
  const [x1, y1, z1] = from;
  const [x2, y2, z2] = to;
  const uv = {
    north: [16 - x2, 16 - y2, 16 - x1, 16 - y1],
    south: [x1, 16 - y2, x2, 16 - y1],
    east: [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    west: [z1, 16 - y2, z2, 16 - y1],
    up: [x1, z1, x2, z2],
    down: [x1, 16 - z2, x2, 16 - z1],
  };
  const out = { name, from, to, faces: {} };
  for (const f of faces) {
    out.faces[f] = { uv: uv[f], texture };
    if (tint) out.faces[f].tintindex = 0;
  }
  if (from[1] === 0 && out.faces.down) out.faces.down.cullface = "down";
  return out;
}

const T = "#taca";
const model = {
  credit: "ShifuRace - Troféu de Corrida (gerado por tools/gerar_recursos.js)",
  ambientocclusion: false,
  texture_size: [16, 16],
  textures: {
    taca: "shifurace:block/trofeu_corrida_taca",
    base: "shifurace:block/trofeu_corrida_base",
    placa: "shifurace:block/trofeu_corrida_placa",
    bandeira: "shifurace:block/trofeu_corrida_bandeira",
    mastro: "shifurace:block/trofeu_corrida_mastro",
    particle: "shifurace:block/trofeu_corrida_taca",
  },
  elements: [
    element("base_inferior", [3, 0, 3], [13, 1.5, 13], "#base"),
    element("base_superior", [4, 1.5, 4], [12, 3, 12], "#base"),
    element("placa", [5.5, 0.3, 2.8], [10.5, 2.7, 3], "#placa", false, ["north", "east", "west", "up", "down"]),
    element("haste", [7, 3, 7], [9, 6, 9], T, true),
    element("no", [6.5, 6, 6.5], [9.5, 7, 9.5], T, true),
    element("taca", [4.5, 7, 4.5], [11.5, 12, 11.5], T, true),
    element("borda", [4, 12, 4], [12, 13, 12], T, true),
    element("alca_esq_cima", [2.5, 11, 7.5], [4, 12, 8.5], T, true),
    element("alca_esq_lado", [2.5, 8.5, 7.5], [3.5, 11, 8.5], T, true),
    element("alca_esq_baixo", [2.5, 8, 7.5], [4.5, 8.5, 8.5], T, true),
    element("alca_dir_cima", [12, 11, 7.5], [13.5, 12, 8.5], T, true),
    element("alca_dir_lado", [12.5, 8.5, 7.5], [13.5, 11, 8.5], T, true),
    element("alca_dir_baixo", [11.5, 8, 7.5], [13.5, 8.5, 8.5], T, true),
    element("mastro", [12.6, 1.5, 12.6], [13.1, 16, 13.1], "#mastro"),
    element("bandeira", [8.6, 12, 12.75], [12.6, 15.5, 12.95], "#bandeira"),
  ],
  display: {
    gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] },
    ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
    fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
    head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
    thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
    firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
  },
};
save("models/block/trofeu.json", Buffer.from(JSON.stringify(model, null, 2) + "\n"));
