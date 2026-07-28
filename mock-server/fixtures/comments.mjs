// Comment fixtures - 3+ per gallery

const usernames = [
  'manga_reader_99', 'doujin_fan', 'art_appreciator', 'casual_browser',
  'collector_pro', 'night_owl', 'scanlation_team', 'random_user_42',
];

const commentTexts = [
  'Great art quality! The line work is really clean.',
  'Thanks for uploading! Been looking for this one.',
  'The story is a bit weak but the art makes up for it.',
  'One of the best in this category. Highly recommend.',
  'Pages 15-20 are absolutely stunning. The coloring is perfect.',
  'Does anyone have a higher resolution version?',
  'This artist never disappoints. Adding to favorites.',
  'The translation could be better but still enjoyable.',
  'Classic work. Still holds up after all these years.',
  'Interesting style. Not for everyone but I loved it.',
  'The character designs are top notch.',
  'Would love to see more from this circle.',
];

let commentId = 5000;

function makeComments(gid, count) {
  const comments = [];
  for (let i = 0; i < count; i++) {
    const year = 2023 + Math.floor(Math.random() * 3);
    const month = String(Math.floor(Math.random() * 12) + 1).padStart(2, '0');
    const day = String(Math.floor(Math.random() * 28) + 1).padStart(2, '0');
    comments.push({
      id: ++commentId,
      uploader: usernames[Math.floor(Math.random() * usernames.length)],
      comment: commentTexts[Math.floor(Math.random() * commentTexts.length)],
      time: `${year}-${month}-${day} ${String(Math.floor(Math.random() * 24)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}`,
      score: Math.floor(Math.random() * 21) - 5, // -5 to +15
    });
  }
  return comments;
}

// Pre-generate comments for some galleries
const commentCache = new Map();

export function getCommentsForGallery(gid) {
  if (!commentCache.has(gid)) {
    const count = 3 + Math.floor(Math.random() * 4); // 3-6 comments
    commentCache.set(gid, makeComments(gid, count));
  }
  return { comments: commentCache.get(gid) };
}
