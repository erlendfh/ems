/*
 * Copyright 2009 JavaBin
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package no.java.ems.server.cli;

import no.java.ems.dao.EventDao;
import no.java.ems.dao.SessionDao;
import no.java.ems.server.domain.Event;
import no.java.ems.server.domain.Session;
import no.java.ems.server.search.SearchService;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class Reindex {

    private EventDao eventDao;
    private SessionDao sessionDao;
    private SearchService searchService;

    public static void main(String[] args) throws Exception {

        EmsCommandLine commandLine = new EmsOptions("ems-reindex").
            parse(args);

        if (commandLine == null) {
            return;
        }

        System.err.println("TODO: Implement this");
//        EmsServices emsServices = new EmsServices(commandLine.getEmsHome(), 0, true, false, 0, false);
//
//        new Reindex(emsServices.getEventDao(), emsServices.getSessionDao(), emsServices.getSearchService()).work();

        // Some stuff is keeping the JVM alive..
        System.exit(0);
    }

    public Reindex(EventDao eventDao, SessionDao sessionDao, SearchService searchService) {
        this.eventDao = eventDao;
        this.sessionDao = sessionDao;
        this.searchService = searchService;
    }

    private void work() throws Exception {
        System.out.println("Indexing all events...");

        for (Event event : eventDao.getEvents()) {
            indexEvent(event);
        }

        SearchService.IndexStatistics statistics = searchService.getIndexStatistics();

        System.out.println("The index now contain " + statistics.numberOfDocuments + " documents.");
    }

    private void indexEvent(Event event) {
        System.out.println("Indexing event: " + event.getName() + " (" + event.getId() + ")...");

        for (Session session : sessionDao.getSessions(event.getId())) {
            indexSession(session);
        }
    }

    private void indexSession(Session session) {
        System.out.println("Indexing session: " + session.getTitle() + " (" + session.getId() + ")...");
        searchService.update(session);
    }
}
