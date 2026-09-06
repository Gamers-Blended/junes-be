// Removes synthetic products inserted by seed-products.js, identified by their
// tagged description prefix. Run this after a load-testing session concludes
// so mock data never lingers in a database that also holds real catalog items.
//
// Run: docker exec -i junes-be-mongodb-1 mongosh "mongodb://user:password@localhost:27017/local?authSource=admin" < load-testing/mongo/cleanup-products.js

const SEED_TAG = "Load-test seed product";

const result = db.products.deleteMany({ description: new RegExp("^" + SEED_TAG) });
print("Deleted seeded products: " + result.deletedCount);
print("Remaining products: " + db.products.countDocuments({}));
