// Gallery fixture data - 24 items covering all 10 categories
// Category bit values match Android EhConfig: 1=Misc, 2=Doujinshi, 4=Manga, 8=Artist CG, 16=Game CG, 32=Image Set, 64=Cosplay, 128=Asian Porn, 256=Non-H, 512=Western

const CATEGORIES = {
  1: 'Misc',
  2: 'Doujinshi',
  4: 'Manga',
  8: 'Artist CG',
  16: 'Game CG',
  32: 'Image Set',
  64: 'Cosplay',
  128: 'Asian Porn',
  256: 'Non-H',
  512: 'Western',
};

const MONTH_NAMES = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

const CATEGORY_COLORS = {
  1: '#607d8b',
  2: '#f44336',
  4: '#ff9800',
  8: '#4caf50',
  16: '#8bc34a',
  32: '#00bcd4',
  64: '#e91e63',
  128: '#795548',
  256: '#2196f3',
  512: '#9c27b0',
};

function makeGallery(gid, category, title, titleJpn, opts = {}) {
  const pages = opts.pages || Math.floor(Math.random() * 290) + 10;
  const rating = opts.rating ?? +(Math.random() * 5).toFixed(2);
  const languages = ['EN', 'JP', 'ZH', 'KO', 'N/A'];
  const lang = opts.language || languages[Math.floor(Math.random() * languages.length)];
  const uploaders = ['uploader_san', 'manga_king', 'doujin_lover', 'art_master', 'scan_pro', 'nhentai_bot', 'gallery_admin'];
  const tagPool = [
    'female:sole female', 'male:sole male', 'group:circle name', 'artist:artist name',
    'parody:original', 'character:char name', 'female:stockings', 'male:glasses',
    'female:long hair', 'male:short hair', 'full color', 'anthology', 'story arc',
    'uncensored', 'multi-work series', 'tankoubon', 'webtoon', 'sample',
  ];
  const numTags = Math.floor(Math.random() * 6) + 3;
  const tags = [];
  for (let i = 0; i < numTags; i++) {
    tags.push(tagPool[Math.floor(Math.random() * tagPool.length)]);
  }

  const year = 2020 + Math.floor(Math.random() * 6);
  const month = String(Math.floor(Math.random() * 12) + 1).padStart(2, '0');
  const day = String(Math.floor(Math.random() * 28) + 1).padStart(2, '0');
  const token = Array.from(
    { length: 10 },
    () => '0123456789abcdef'[Math.floor(Math.random() * 16)],
  ).join('');
  const hour = String(Math.floor(Math.random() * 24)).padStart(2, '0');
  const minute = String(Math.floor(Math.random() * 60)).padStart(2, '0');

  return {
    gid,
    token,
    galleryUrl: `https://e-hentai.org/g/${gid}/${token}/`,
    title,
    titleJpn,
    thumb: `/api/v1/image/mock-thumb.svg`,
    category,
    posted: `${day} ${MONTH_NAMES[Number(month) - 1]} ${year}, ${hour}:${minute}`,
    uploader: opts.uploader || uploaders[Math.floor(Math.random() * uploaders.length)],
    rating,
    rated: Math.random() > 0.7,
    simpleLanguage: lang,
    simpleTags: [...new Set(tags)],
    thumbWidth: 100,
    thumbHeight: 140,
    pages,
    favoriteSlot: opts.favoriteSlot ?? -2,
    favoriteName: opts.favoriteName ?? null,
    tags: [...new Set(tags)].map(t => {
      const parts = t.split(':');
      return parts.length > 1
        ? { namespace: parts[0], tag: parts.slice(1).join(':') }
        : { namespace: null, tag: t };
    }),
    imageUrl: `/api/v1/image/mock-thumb.svg`,
  };
}

export const galleries = [
  makeGallery(2801001, 2, '[Circle Name] Summer Festival Doujinshi Vol.3', '[サークル名] 夏祭り同人誌 Vol.3', { pages: 42, rating: 4.35, language: 'JP' }),
  makeGallery(2801002, 2, '[Artist] My Heroine Academia Parody', '[作家] 僕のヒロインアカデミア パロディ', { pages: 28, rating: 3.82, language: 'EN' }),
  makeGallery(2801003, 2, '[Circle] Touhou Project - Scarlet Devil Mansion', '[サークル] 東方Project - 紅魔館', { pages: 56, rating: 4.71, language: 'JP' }),
  makeGallery(2801004, 4, 'Weekly Manga Chapter 245 - The Final Battle', '週刊漫画 第245話 - 最終決戦', { pages: 18, rating: 3.15, language: 'EN' }),
  makeGallery(2801005, 4, 'Monthly Serial - Love Comedy Vol.12', '月刊連載 - ラブコメディ Vol.12', { pages: 195, rating: 4.02, language: 'JP' }),
  makeGallery(2801006, 4, 'Tankoubon Collection - Fantasy Adventure', '単行本コレクション - ファンタジーアドベンチャー', { pages: 280, rating: 4.55, language: 'ZH' }),
  makeGallery(2801007, 8, '[Artist] Digital Art Collection 2024', '[作家] デジタルアートコレクション 2024', { pages: 85, rating: 4.88, language: 'N/A' }),
  makeGallery(2801008, 8, '[Artist] Fantasy CG Set - Dragon Knights', '[作家] ファンタジーCG集 - ドラゴンナイツ', { pages: 120, rating: 4.62, language: 'EN' }),
  makeGallery(2801009, 8, '[Circle] Sci-Fi Illustration Pack', '[サークル] SFイラストパック', { pages: 64, rating: 3.95, language: 'N/A' }),
  makeGallery(2801010, 512, '[Western] Superhero Parody Comic', '[欧米] スーパーヒーローパロディコミック', { pages: 32, rating: 3.45, language: 'EN' }),
  makeGallery(2801011, 512, '[Artist] Western Fantasy Art Book', '[作家] 西洋ファンタジーアートブック', { pages: 48, rating: 4.12, language: 'EN' }),
  makeGallery(2801012, 512, '[Western] Indie Comic Anthology Vol.5', '[欧米] インディーズコミックアンソロジー Vol.5', { pages: 156, rating: 3.78, language: 'EN' }),
  makeGallery(2801013, 256, 'Landscape Photography - Japanese Gardens', '風景写真 - 日本庭園', { pages: 75, rating: 4.25, language: 'JP' }),
  makeGallery(2801014, 256, 'Tutorial - Digital Painting Techniques', 'チュートリアル - デジタルペインティング技法', { pages: 42, rating: 4.68, language: 'EN' }),
  makeGallery(2801015, 256, 'Nature Documentary Stills Collection', '自然ドキュメンタリースチルコレクション', { pages: 200, rating: 3.55, language: 'N/A' }),
  makeGallery(2801016, 32, '[Artist] Complete Illustration Set 2020-2025', '[作家] コンプリートイラストセット 2020-2025', { pages: 300, rating: 4.92, language: 'JP' }),
  makeGallery(2801017, 32, 'Game Concept Art Collection', 'ゲームコンセプトアートコレクション', { pages: 180, rating: 4.45, language: 'EN' }),
  makeGallery(2801018, 32, '[Circle] Anime Key Visual Archive', '[サークル] アニメキービジュアルアーカイブ', { pages: 95, rating: 4.33, language: 'JP' }),
  makeGallery(2801019, 64, 'Cosplay Photo Set - Anime Convention 2024', 'コスプレ写真集 - アニメコンベンション 2024', { pages: 55, rating: 3.88, language: 'N/A' }),
  makeGallery(2801020, 64, '[Photographer] Character Cosplay Collection', '[写真家] キャラクターコスプレコレクション', { pages: 88, rating: 4.15, language: 'ZH' }),
  makeGallery(2801021, 128, '[Studio] Asian Model Photobook Vol.8', '[スタジオ] アジアンモデルフォトブック Vol.8', { pages: 110, rating: 3.62, language: 'N/A' }),
  makeGallery(2801022, 128, '[Photographer] Gravure Collection 2025', '[写真家] グラビアコレクション 2025', { pages: 72, rating: 3.95, language: 'JP' }),
  makeGallery(2801023, 1, 'Miscellaneous Art Dump - Various Artists', 'その他アートまとめ - 様々な作家', { pages: 250, rating: 2.85, language: 'N/A' }),
  makeGallery(2801024, 16, '[Game] Visual Novel CG Pack - Romance Edition', '[ゲーム] ビジュアルノベルCGパック - ロマンスエディション', { pages: 145, rating: 4.08, language: 'JP' }),
];

export function searchGalleries({ keyword, category, page = 0, pageSize = 20 }) {
  let results = [...galleries];

  if (keyword) {
    const kw = keyword.toLowerCase();
    results = results.filter(g =>
      (g.title && g.title.toLowerCase().includes(kw)) ||
      (g.titleJpn && g.titleJpn.toLowerCase().includes(kw)) ||
      (g.simpleTags && g.simpleTags.some(t => t.toLowerCase().includes(kw)))
    );
  }

  if (category && category > 0) {
    results = results.filter(g => (g.category & category) === 0);
  }

  const total = results.length;
  const start = page * pageSize;
  const data = results.slice(start, start + pageSize);

  return { success: true, data, total };
}

export function getGalleryByGid(gid) {
  return galleries.find(g => g.gid === gid) || null;
}

export { CATEGORIES, CATEGORY_COLORS };
