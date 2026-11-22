const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_prism_123";
const BOB_UID = "bob_prism_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all Prism collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/playlists`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/ytSubscriptions`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/folders/folder_1`).set({
      name: "Notes",
      iconKey: "folder",
      systemFolder: true,
      sortOrder: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Owner can create, list, partially update, and delete folders and notes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/folders/notes_default`).set({
      name: "Notes",
      iconKey: "folder",
      systemFolder: true,
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).set({
      folderId: "notes_default",
      title: "UI/UX Principles",
      body: "Liquid Glass refraction and Fluid Interfaces.",
      snippet: "Liquid Glass refraction...",
      tags: ["Design-ideas"],
      checklists: [{ text: "Study AGSL", checked: false }],
      highlights: [{ text: "Liquid Glass", colorKey: "blue" }],
      attachments: [],
      pinned: false,
      locked: false,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notes`).get());

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).update({
      pinned: true,
      updatedAt: updated,
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).update({
      createdAt: updated,
      updatedAt: updated,
    })
  );

  // Reject ghost field / @DocumentId 'id' leakage
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_bad`).set({
      id: "note_bad",
      folderId: "notes_default",
      title: "Bad",
      body: "",
      snippet: "",
      tags: [],
      checklists: [],
      highlights: [],
      attachments: [],
      pinned: false,
      locked: false,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).delete());
});

test("Owner can manage Music playlists, favorites, listening history, and YouTube library", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/playlists/pl_fall`).set({
      name: "Fall",
      coverStyle: 2,
      tracks: [
        {
          videoId: "abc123",
          title: "Midnight Sun",
          artist: "Zara Larsson",
          thumbnailUrl: "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
          durationSec: 210,
        },
      ],
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/musicFavorites/abc123`).set({
      videoId: "abc123",
      title: "Midnight Sun",
      artist: "Zara Larsson",
      thumbnailUrl: "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
      durationSec: 210,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/listeningHistory/hist_1`).set({
      videoId: "abc123",
      title: "Midnight Sun",
      artist: "Zara Larsson",
      album: "Midnight Sun",
      thumbnailUrl: "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
      durationSec: 210,
      listenedSec: 185,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/ytSubscriptions/ch_f1`).set({
      channelUrl: "https://www.youtube.com/@Formula1",
      channelName: "FORMULA 1",
      avatarUrl: "https://example.com/f1.jpg",
      subscriberText: "11M subscribers",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/ytSavedVideos/vid_f1`).set({
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      title: "F1 | Official Trailer",
      uploaderName: "FORMULA 1",
      thumbnailUrl: "https://example.com/thumb.jpg",
      durationSec: 160,
      liked: true,
      saved: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject future timestamp spoofing
  const future = new Date(Date.now() + 120000);
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/playlists/pl_future`).set({
      name: "Future",
      coverStyle: 0,
      tracks: [],
      createdAt: future,
      updatedAt: future,
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's data", async () => {
  const past = new Date(Date.now() - 8000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`users/${ALICE_UID}/notes/secret_1`).set({
      folderId: "notes_default",
      title: "Private",
      body: "Top secret",
      snippet: "Top secret",
      tags: [],
      checklists: [],
      highlights: [],
      attachments: [],
      pinned: false,
      locked: true,
      deleted: false,
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/notes/secret_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/notes/secret_1`).update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/notes/secret_1`).delete());
});
