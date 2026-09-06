// Seeds synthetic MongoDB products for load testing product-listing-throughput.js
// against a realistically-sized catalog instead of an (almost) empty collection.
//
// Every seeded doc is tagged via a description prefix so it can be removed
// cleanly afterwards without touching real catalog data - see cleanup-products.js.
//
// Run:   docker exec -i junes-be-mongodb-1 mongosh "mongodb://user:password@localhost:27017/local?authSource=admin" < load-testing/mongo/seed-products.js
// Undo:  docker exec -i junes-be-mongodb-1 mongosh "mongodb://user:password@localhost:27017/local?authSource=admin" < load-testing/mongo/cleanup-products.js

const SEED_TAG = "Load-test seed product";
const PRODUCTS_PER_PLATFORM = 60;

const PLATFORMS = ["ps4", "ps5", "xbo", "xsx", "nsw", "nsw2", "pc"];
const REGIONS = ["asia", "us", "eur"];
const EDITIONS = ["std", "se", "ce"];
const GENRES = ["Action", "RPG", "Shooter", "Platformer", "Sports", "Adventure", "Puzzle", "Racing"];
const LANGUAGES = ["English", "Japanese", "French", "German", "Spanish"];
const PLAYER_COUNTS = ["1", "1-2", "1-4", "Online Multiplayer"];
const PUBLISHERS = ["Nexora Games", "Ironclad Interactive", "Starlight Studios", "Vertex Entertainment", "Redwood Interactive"];

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function pickSet(arr, count) {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, count);
}

function randomDate(startYear, endYear) {
  const start = new Date(startYear, 0, 1).getTime();
  const end = new Date(endYear, 11, 31).getTime();
  return new Date(start + Math.random() * (end - start));
}

// Clear any previous run's seed data first, so re-running this script is idempotent.
const preDelete = db.products.deleteMany({ description: new RegExp("^" + SEED_TAG) });
print("Removed previous seed data: " + preDelete.deletedCount);

const docs = [];
let counter = 0;

for (const platform of PLATFORMS) {
  for (let i = 0; i < PRODUCTS_PER_PLATFORM; i++) {
    counter++;
    const name = "Synthetic Quest " + platform.toUpperCase() + " #" + counter;
    const releaseDate = randomDate(2018, 2025);

    docs.push({
      name: name,
      slug: name.toLowerCase().replace(/[^a-z0-9]+/g, "-"),
      description: SEED_TAG + " - generated for k6 load testing, safe to delete.",
      price: NumberDecimal((19.99 + Math.random() * 60).toFixed(2)),
      platform: platform,
      region: pick(REGIONS),
      edition: pick(EDITIONS),
      publisher: pick(PUBLISHERS),
      release_date: releaseDate,
      series: [],
      genres: pickSet(GENRES, 1 + Math.floor(Math.random() * 2)),
      languages: pickSet(LANGUAGES, 1 + Math.floor(Math.random() * 3)),
      number_of_players: [pick(PLAYER_COUNTS)],
      weight: NumberDecimal((0.05 + Math.random() * 0.15).toFixed(2)),
      units_sold: Math.floor(Math.random() * 500000),
      stock: Math.floor(Math.random() * 500),
      product_image_url: "https://example.com/images/placeholder.jpg",
      image_url_list: ["https://example.com/images/placeholder.jpg"],
      created_on: new Date(),
    });
  }
}

const result = db.products.insertMany(docs);
print("Inserted: " + Object.keys(result.insertedIds).length);
print("Total products now: " + db.products.countDocuments({}));
