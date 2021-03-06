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

package no.java.ems.cli;

import no.java.ems.external.v1.EventV1;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class ListEvents extends AbstractCli {
    public ListEvents() {
        super("list-events");
    }

    public static void main(String[] args) throws Exception {
        new ListEvents().doMain(args);
    }

    public void work() throws Exception {
        System.err.println("Events: ");
        for (EventV1 event : getEms().getEvents().getEvent()) {
            System.err.println(event.getUuid() + ":" + event.getName());
        }
    }
}
