/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.opensolaris.opengrok.util.TestRepository;
import static org.junit.Assert.*;

/**
 * Tests for MercurialRepository.
 */
public class MercurialRepositoryTest {

    /**
     * Revision numbers present in the Mercurial test repository, in the
     * order they are supposed to be returned from getHistory(),
     * that is latest changeset first.
     */
    private static final String[] REVISIONS = {
        "8:6a8c423f5624", "7:db1394c05268", "6:e386b51ddbcc",
        "5:8706402863c6", "4:e494d67af12f", "3:2058725c1470",
        "2:585a1b3f2efb", "1:f24a5fd7a85d", "0:816b6279ae9c"
    };

    private TestRepository repository;

    /**
     * Set up a test repository. Should be called by the tests that need it.
     * The test repository will be destroyed automatically when the test
     * finishes.
     */
    private void setUpTestRepository() throws IOException {
        repository = new TestRepository();
        repository.create(getClass().getResourceAsStream("repositories.zip"));
    }

    @After
    public void tearDown() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @Test
    public void testGetHistory() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);
        History hist = mr.getHistory(root);
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(REVISIONS.length, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            assertEquals(REVISIONS[i], e.getRevision());
            assertNotNull(e.getAuthor());
            assertNotNull(e.getDate());
            assertNotNull(e.getFiles());
            assertNotNull(e.getMessage());
        }
    }

    /**
     * Test that subset of changesets can be extracted based on penultimate
     * revision number.
     * @throws Exception 
     */
    @Test
    public void testGetHistoryPartial() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);
        // Get all but the oldest revision.
        History hist = mr.getHistory(root, REVISIONS[REVISIONS.length - 1]);
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(REVISIONS.length - 1, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            assertEquals(REVISIONS[i], e.getRevision());
            assertNotNull(e.getAuthor());
            assertNotNull(e.getDate());
            assertNotNull(e.getFiles());
            assertNotNull(e.getMessage());
        }
    }
    
    /**
     * Test that it is possible to get contents of last revision of a text
     * file.
     */
    @Test
    public void testGetHistoryGet() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);
        String exp_str = "This will be a first novel of mine.\n" +
            "\n" +
            "Chapter 1.\n" +
            "\n" +
            "Let's write some words. It began like this:\n" +
            "\n" +
            "...\n";
        byte[] buffer = new byte[1024];
        
        InputStream input = mr.getHistoryGet(root.getCanonicalPath(),
                "novel.txt", REVISIONS[0]);
        assertNotNull(input);

        String str = "";
        int len;
        while ((len = input.read(buffer)) > 0) {
            str += new String(buffer, 0, len);
        }
        assertNotSame(str.length(), 0);
        assertEquals(exp_str, str);
    }
    
    /**
     * Test that {@code getHistoryGet()} returns historical contents of 
     * renamed file.
     */
    @Test
    public void testGetHistoryGetRenamed() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);
        String exp_str = "This is totally plaintext file.\n";
        byte[] buffer = new byte[1024];
        
        /* 
         * In our test repository the file was renamed twice since 
         * revision 3.
         */
        InputStream input = mr.getHistoryGet(root.getCanonicalPath(),
                "novel.txt", "3");
        assert(input != null);
        int len = input.read(buffer);
        assert(len != -1);
        String str = new String(buffer, 0, len);
        assert(str.compareTo(exp_str) == 0);
    }  

    /**
     * Test that {@code getHistory()} throws an exception if the revision
     * argument doesn't match any of the revisions in the history.
     */
    @Test
    public void testGetHistoryWithNoSuchRevision() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);

        // Get the sequence number and the hash from one of the revisions.
        String[] revisionParts = REVISIONS[1].split(":");
        assertEquals(2, revisionParts.length);
        int number = Integer.parseInt(revisionParts[0]);
        String hash = revisionParts[1];

        // Construct a revision identifier that doesn't exist.
        String constructedRevision = (number + 1) + ":" + hash;
        try {
            mr.getHistory(root, constructedRevision);
            fail("getHistory() should have failed");
        } catch (HistoryException he) {
            String msg = he.getMessage();
            if (msg != null && msg.contains("not found in the repository")) {
                // expected exception, do nothing
            } else {
                // unexpected exception, rethrow it
                throw he;
            }
        }
    }

}
