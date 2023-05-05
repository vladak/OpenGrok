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
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2020, 2023, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.Filter;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.util.TestRepository;

/**
 * Test file based history cache with special focus on incremental reindex.
 *
 * @author Vladimir Kotal
 */
class FileHistoryCacheTest {

    private static final String SUBVERSION_REPO_LOC = "subversion";

    private static final String SVN_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    private TestRepository repositories;
    private FileHistoryCache cache;

    private boolean savedFetchHistoryWhenNotInCache;
    private boolean savedIsHandleHistoryOfRenamedFiles;
    private boolean savedIsTagsEnabled;

    /**
     * Set up the test environment with repositories and a cache instance.
     */
    @BeforeEach
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResource("/repositories"));

        // Needed for HistoryGuru to operate normally.
        env.setRepositories(repositories.getSourceRoot());

        cache = new FileHistoryCache();
        cache.initialize();

        savedFetchHistoryWhenNotInCache = env.isFetchHistoryWhenNotInCache();
        savedIsHandleHistoryOfRenamedFiles = env.isHandleHistoryOfRenamedFiles();
        savedIsTagsEnabled = env.isTagsEnabled();
    }

    /**
     * Clean up after the test. Remove the test repositories.
     */
    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;

        env.setFetchHistoryWhenNotInCache(savedFetchHistoryWhenNotInCache);
        env.setIgnoredNames(new IgnoredNames());
        env.setIncludedNames(new Filter());
        env.setHandleHistoryOfRenamedFiles(savedIsHandleHistoryOfRenamedFiles);
        env.setTagsEnabled(savedIsTagsEnabled);
    }

    /**
     * Assert that two lists of HistoryEntry objects are equal.
     *
     * @param expected the expected list of entries
     * @param actual the actual list of entries
     * @param isdir was the history generated for a directory
     * @throws AssertionError if the two lists don't match
     */
    private void assertSameEntries(List<HistoryEntry> expected, List<HistoryEntry> actual, boolean isdir) {
        assertEquals(expected.size(), actual.size(), "Unexpected size");
        Iterator<HistoryEntry> actualIt = actual.iterator();
        for (HistoryEntry expectedEntry : expected) {
            assertSameEntry(expectedEntry, actualIt.next(), isdir);
        }
        assertFalse(actualIt.hasNext(), "More entries than expected");
    }

    /**
     * Assert that two HistoryEntry objects are equal.
     *
     * @param expected the expected instance
     * @param actual the actual instance
     * @param isdir was the history generated for directory
     * @throws AssertionError if the two instances don't match
     */
    private void assertSameEntry(HistoryEntry expected, HistoryEntry actual, boolean isdir) {
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getRevision(), actual.getRevision());
        assertEquals(expected.getDate(), actual.getDate());
        assertEquals(expected.getMessage(), actual.getMessage());
        if (isdir) {
            assertEquals(expected.getFiles().size(), actual.getFiles().size());
        } else {
            assertEquals(0, actual.getFiles().size());
        }
    }

    /**
     * Test {@link FileHistoryCache#fillLastHistoryEntries(List)}, in particular that it
     * returns {@code false} and resets date/descriptions if some entries cannot be filled.
     */
    @Test
    void testFillLastHistoryEntriesAllOrNothing() throws Exception {
        File repositoryRoot = new File(repositories.getSourceRoot(), "git");
        Repository repository = RepositoryFactory.getRepository(repositoryRoot);

        // This file will be created without any repository involvement, therefore it will not be possible
        // to get history entry for it. This should make fillLastHistoryEntries() to return false.
        File subFile = new File(repositoryRoot, "file.txt");
        assertFalse(subFile.exists());
        assertTrue(subFile.createNewFile());

        FileHistoryCache spyCache = Mockito.spy(cache);
        spyCache.clear(repository);
        History historyToStore = repository.getHistory(repositoryRoot);
        spyCache.store(historyToStore, repository);

        File[] files = repositoryRoot.listFiles();
        assertNotNull(files);
        assertTrue(files.length > 0);
        List<DirectoryEntry> directoryEntries = Arrays.stream(files).map(DirectoryEntry::new).
                collect(Collectors.toList());

        assertFalse(spyCache.fillLastHistoryEntries(directoryEntries));
        assertEquals(0, (int) directoryEntries.stream().filter(e -> e.getDate() != null).count());
        assertEquals(0, (int) directoryEntries.stream().filter(e -> e.getDescription() != null).count());

        // Cleanup.
        cache.clear(repository);
    }
}
