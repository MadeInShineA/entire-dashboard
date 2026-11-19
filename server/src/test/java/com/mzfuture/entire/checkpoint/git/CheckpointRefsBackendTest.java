package com.mzfuture.entire.checkpoint.git;

import com.mzfuture.entire.config.GitProperties;
import com.mzfuture.entire.gitsync.service.impl.GitOperationServiceImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Entire CLI 0.13+ "refs" checkpoint backend: refs/entire/checkpoints/<shard>/<ulid>, files at tree root.
class CheckpointRefsBackendTest {

    private static final String ULID = "01M7TAEQCJ7PEWZ0R86Y40ER61";
    private static final String REF = CheckpointGitReader.CHECKPOINT_REFS_PREFIX + "61/" + ULID;

    @Test
    void fetchesAndReadsCheckpointFromRefsBackend(@TempDir Path tmp) throws Exception {
        File remoteDir = tmp.resolve("remote").toFile();
        try (Git git = Git.init().setDirectory(remoteDir).setInitialBranch("main").call()) {
            Files.writeString(remoteDir.toPath().resolve("README.md"), "hello\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("Add readme\n\nEntire-Checkpoint: " + ULID)
                    .setAuthor("t", "t@t").setCommitter("t", "t@t").call();
            writeOrphanCheckpoint(git.getRepository());
        }
        String url = "file://" + remoteDir.getAbsolutePath();

        GitProperties props = new GitProperties();
        props.setDataPath(tmp.resolve("data").toString());
        GitOperationServiceImpl ops = new GitOperationServiceImpl(props);

        List<String> remoteRefs = ops.listRemoteRefs(url, CheckpointGitReader.CHECKPOINT_REFS_PREFIX);
        assertEquals(List.of(REF), remoteRefs);

        ops.cloneRepository(1L, url, "main", 0);
        String prefix = CheckpointGitReader.CHECKPOINT_REFS_PREFIX;
        ops.fetchRefSpecs(1L, url, List.of("+" + prefix + "*:" + prefix + "*"));

        CheckpointGitReader reader = new CheckpointGitReader(ops);
        Map<String, String> refs = reader.listCheckpointRefs(1L);
        assertTrue(refs.containsKey(ULID), "checkpoint ref should be fetched locally: " + refs);

        Optional<CheckpointGitReader.CheckpointLocation> loc = CheckpointGitReader.locateCheckpoint(ULID, refs, null);
        assertTrue(loc.isPresent());
        assertEquals("", loc.get().basePath());
        assertTrue(reader.getFileContent(1L, loc.get().revision(), "metadata.json").orElseThrow().contains(ULID));
        assertEquals("{\"x\":1}\n", reader.getFileContent(1L, loc.get().revision(), "0/full.jsonl").orElseThrow());
    }

    @Test
    void fallsBackToLegacyBranchLayout() {
        Optional<CheckpointGitReader.CheckpointLocation> loc =
                CheckpointGitReader.locateCheckpoint("ae54d67dddc9", Map.of(), "abc123");
        assertTrue(loc.isPresent());
        assertEquals("abc123", loc.get().revision());
        assertEquals("ae/54d67dddc9/", loc.get().basePath());
        assertTrue(CheckpointGitReader.locateCheckpoint("ae54d67dddc9", Map.of(), null).isEmpty());
    }

    @Test
    void trailerPatternMatchesLegacyAndUlidIds() throws Exception {
        Field f = Class.forName("com.mzfuture.entire.checkpoint.service.impl.CheckpointSyncServiceImpl")
                .getDeclaredField("TRAILER_PATTERN");
        f.setAccessible(true);
        Pattern p = (Pattern) f.get(null);
        Matcher legacy = p.matcher("msg\n\nEntire-Checkpoint: c14692dbdbc6\n");
        assertTrue(legacy.find());
        assertEquals("c14692dbdbc6", legacy.group(1));
        Matcher ulid = p.matcher("msg\n\nEntire-Checkpoint: " + ULID);
        assertTrue(ulid.find());
        assertEquals(ULID, ulid.group(1));
    }

    private static void writeOrphanCheckpoint(Repository repo) throws Exception {
        try (ObjectInserter ins = repo.newObjectInserter()) {
            ObjectId meta = ins.insert(Constants.OBJ_BLOB,
                    ("{\"checkpoint_id\":\"" + ULID + "\",\"sessions\":[{\"metadata\":\"/0/metadata.json\"}]}")
                            .getBytes(StandardCharsets.UTF_8));
            ObjectId full = ins.insert(Constants.OBJ_BLOB, "{\"x\":1}\n".getBytes(StandardCharsets.UTF_8));
            TreeFormatter sessionTree = new TreeFormatter();
            sessionTree.append("full.jsonl", FileMode.REGULAR_FILE, full);
            ObjectId sessionTreeId = ins.insert(sessionTree);
            TreeFormatter root = new TreeFormatter();
            root.append("0", FileMode.TREE, sessionTreeId);
            root.append("metadata.json", FileMode.REGULAR_FILE, meta);
            ObjectId rootId = ins.insert(root);

            CommitBuilder cb = new CommitBuilder();
            cb.setTreeId(rootId);
            PersonIdent who = new PersonIdent("t", "t@t");
            cb.setAuthor(who);
            cb.setCommitter(who);
            cb.setMessage("Checkpoint: " + ULID);
            ObjectId commitId = ins.insert(cb);
            ins.flush();

            RefUpdate ru = repo.updateRef(REF);
            ru.setNewObjectId(commitId);
            ru.update();
        }
    }
}
