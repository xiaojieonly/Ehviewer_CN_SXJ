// Deterministic test-image generator for the E-Hentai dummy server.
//
// Images are shaped so that download & reading can be verified by eye:
//   - large page number in the center (bitmap font, scale 40)
//   - small gid badge in the top-left corner and total page count bottom-left
//   - hue rotates with gid, diagonal stripes shift with page
// All three formats (PNG/JPEG/GIF) render the exact same pixels.

import zlib from 'node:zlib';
import jpeg from 'jpeg-js';
import gifenc from 'gifenc';

// ------------------------------------------------------- bitmap font (3x5)

// 3x5 bitmap font for digits 0-9 (1 = filled).
const DIGITS = {
  0: ['111', '101', '101', '101', '111'],
  1: ['010', '110', '010', '010', '111'],
  2: ['111', '001', '111', '100', '111'],
  3: ['111', '001', '111', '001', '111'],
  4: ['101', '101', '111', '001', '001'],
  5: ['111', '100', '111', '001', '111'],
  6: ['111', '100', '111', '101', '111'],
  7: ['111', '001', '010', '010', '010'],
  8: ['111', '101', '111', '101', '111'],
  9: ['111', '101', '111', '001', '111'],
};

const GLYPH_W = 3;
const GLYPH_H = 5;

function digitGlyph(d) {
  return DIGITS[d] ?? DIGITS[0];
}

// ------------------------------------------------------------- rendering

const WIDTH = 800;
const HEIGHT = 1120;

function hsvToRgb(h, s, v) {
  const i = Math.floor(h * 6);
  const f = h * 6 - i;
  const p = v * (1 - s);
  const q = v * (1 - f * s);
  const t = v * (1 - (1 - f) * s);
  const r = [v, q, p, p, t, v][i % 6];
  const g = [t, v, v, q, p, p][i % 6];
  const b = [p, p, t, v, v, q][i % 6];
  return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
}

function setPixel(rgb, x, y, color) {
  if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
    return;
  }
  const o = (y * WIDTH + x) * 3;
  rgb[o] = color[0];
  rgb[o + 1] = color[1];
  rgb[o + 2] = color[2];
}

function fillRect(rgb, x0, y0, w, h, color) {
  for (let y = y0; y < y0 + h; y++) {
    for (let x = x0; x < x0 + w; x++) {
      setPixel(rgb, x, y, color);
    }
  }
}

// Draw a string of digits centered at (cx, cy), each digit scaled by `scale`,
// foreground with a black outline so it reads on any background.
function render(gid, page, totalPages) {
  const rgb = new Uint8Array(WIDTH * HEIGHT * 3);
  const baseHue = ((gid * 31) % 360) / 360;
  for (let y = 0; y < HEIGHT; y++) {
    for (let x = 0; x < WIDTH; x++) {
      const stripe = ((x + y) % 40) < 20 ? 0.9 : 0.6;
      const [r, g, b] = hsvToRgb((baseHue + page * 0.02) % 1, 0.45, stripe);
      const o = (y * WIDTH + x) * 3;
      rgb[o] = r;
      rgb[o + 1] = g;
      rgb[o + 2] = b;
    }
  }

  const white = [255, 255, 255];
  const yellow = [255, 230, 80];

  // Top-left: gid badge "G 1001"
  drawText(rgb, `G ${gid}`, 90, 70, 14, yellow);
  // Center: huge page number
  drawText(rgb, String(page), WIDTH / 2, HEIGHT / 2, 60, white);
  // Bottom-left: total pages "P 5"
  drawText(rgb, `P ${totalPages}`, 90, HEIGHT - 60, 14, yellow);

  return rgb;
}

// General text drawing: supports digits, space, and the letters G/P.
const LETTERS = {
  G: ['111', '101', '110', '101', '111'],
  P: ['111', '101', '111', '100', '100'],
  ' ': ['000', '000', '000', '000', '000'],
};

function glyphFor(ch) {
  if (ch >= '0' && ch <= '9') {
    return digitGlyph(ch);
  }
  return LETTERS[ch] ?? LETTERS[' '];
}

function drawText(rgb, text, cx, cy, scale, fg) {
  const digitW = GLYPH_W * scale;
  const digitH = GLYPH_H * scale;
  const gap = Math.round(scale / 2);
  const totalW = text.length * digitW + (text.length - 1) * gap;
  const x0 = Math.round(cx - totalW / 2);
  const y0 = Math.round(cy - digitH / 2);
  for (let d = 0; d < text.length; d++) {
    const glyph = glyphFor(text[d]);
    const dx = x0 + d * (digitW + gap);
    for (let gy = 0; gy < GLYPH_H; gy++) {
      for (let gx = 0; gx < GLYPH_W; gx++) {
        if (glyph[gy][gx] !== '1') {
          continue;
        }
        const px = dx + gx * scale;
        const py = y0 + gy * scale;
        fillRect(rgb, px - 2, py - 2, scale + 4, scale + 4, [0, 0, 0]);
        fillRect(rgb, px, py, scale, scale, fg);
      }
    }
  }
}

// ------------------------------------------------------------- encoders

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

export function makePng(gid, page, totalPages = 10) {
  const rgb = render(gid, page, totalPages);
  const raw = Buffer.alloc(HEIGHT * (1 + WIDTH * 3));
  let o = 0;
  for (let y = 0; y < HEIGHT; y++) {
    raw[o++] = 0; // filter: none
    for (let x = 0; x < WIDTH; x++) {
      const i = (y * WIDTH + x) * 3;
      raw[o++] = rgb[i];
      raw[o++] = rgb[i + 1];
      raw[o++] = rgb[i + 2];
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(WIDTH, 0);
  ihdr.writeUInt32BE(HEIGHT, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // color type: truecolor RGB
  const idat = zlib.deflateSync(raw);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', idat),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

export function makeJpeg(gid, page, totalPages = 10) {
  const rgb = render(gid, page, totalPages);
  const encoded = jpeg.encode(
    { data: Buffer.from(rgb), width: WIDTH, height: HEIGHT },
    75,
  );
  return Buffer.from(encoded.data);
}

export function makeGif(gid, page, totalPages = 10) {
  const rgb = render(gid, page, totalPages);
  const { GIFEncoder, quantize, applyPalette } = gifenc;
  const rgba = new Uint8Array(WIDTH * HEIGHT * 4);
  for (let i = 0; i < WIDTH * HEIGHT; i++) {
    rgba[i * 4] = rgb[i * 3];
    rgba[i * 4 + 1] = rgb[i * 3 + 1];
    rgba[i * 4 + 2] = rgb[i * 3 + 2];
    rgba[i * 4 + 3] = 255;
  }
  const palette = quantize(rgba, 256);
  const index = applyPalette(rgba, palette);
  const gif = GIFEncoder();
  gif.writeFrame(index, WIDTH, HEIGHT, { palette });
  gif.finish();
  return Buffer.from(gif.bytes());
}

// --------------------------------------------------------------- API

/**
 * Deterministic test image for (gid, page).
 * @param {number} gid gallery id
 * @param {number} page 1-based page number (rendered on the image)
 * @param {'png'|'jpg'|'jpeg'|'gif'} format
 * @param {number} [totalPages] total page count rendered bottom-left
 * @returns {Buffer}
 */
export function makeImage(gid, page, format, totalPages = 10) {
  switch (format) {
    case 'png':
      return makePng(gid, page, totalPages);
    case 'jpg':
    case 'jpeg':
      return makeJpeg(gid, page, totalPages);
    case 'gif':
      return makeGif(gid, page, totalPages);
    default:
      throw new Error(`Unsupported image format: ${format}`);
  }
}
