package com.mzfuture.entire.checkpoint.git;

import com.mzfuture.entire.config.GitProperties;
import com.mzfuture.entire.gitsync.service.impl.GitOperationServiceImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointGitReaderBlameTest {

    @Test
    void listFilesAndBlameAttributeLinesToCommits(@TempDir Path tmp) throws Exception {
        File repoDir = tmp.resolve("data/1").toFile();
        RevCommit first;
        RevCommit second;
        try (Git git = Git.init().setDirectory(repoDir).setInitialBranch("main").call()) {
            Files.writeString(repoDir.toPath().resolve("a.txt"), "one\ntwo\nthree\n");
            Files.createDirectories(repoDir.toPath().resolve("src"));
            Files.write(repoDir.toPath().resolve("src/bin.dat"), new byte[]{0, 1, 2, 0});
            git.add().addFilepattern(".").call();
            first = git.commit().setMessage("first").setAuthor("alice", "a@a").setCommitter("alice", "a@a").call();

            Files.writeString(repoDir.toPath().resolve("a.txt"), "one\nTWO\nthree\nfour\n");
            git.add().addFilepattern("a.txt").call();
            second = git.commit().setMessage("second").setAuthor("bob", "b@b").setCommitter("bob", "b@b").call();
        }

        GitProperties props = new GitProperties();
        props.setDataPath(tmp.resolve("data").toString());
        CheckpointGitReader reader = new CheckpointGitReader(new GitOperationServiceImpl(props));

        assertEquals(List.of("a.txt", "src/bin.dat"), reader.listFiles(1L, "main").files());
        assertFalse(reader.isShallow(1L));

        CheckpointGitReader.BlameData blame = reader.blame(1L, "main", "a.txt", 1000).orElseThrow();
        assertFalse(blame.binary());
        assertEquals(second.getName(), blame.headSha());
        List<String> shas = blame.lines().stream().map(CheckpointGitReader.BlameLine::commitSha).toList();
        assertEquals(List.of(first.getName(), second.getName(), first.getName(), second.getName()), shas);
        assertEquals("TWO", blame.lines().get(1).content());
        assertEquals("bob", blame.lines().get(1).authorName());

        CheckpointGitReader.BlameData truncated = reader.blame(1L, "main", "a.txt", 2).orElseThrow();
        assertTrue(truncated.truncated());
        assertEquals(2, truncated.lines().size());

        assertTrue(reader.blame(1L, "main", "src/bin.dat", 1000).orElseThrow().binary());
        assertTrue(reader.blame(1L, "main", "missing.txt", 1000).isEmpty());
        assertTrue(reader.blame(1L, "main", "src", 1000).isEmpty());
    }
}
