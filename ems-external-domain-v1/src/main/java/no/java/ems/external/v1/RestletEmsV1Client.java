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

package no.java.ems.external.v1;

import fj.F;
import fj.F2;
import static fj.Function.compose;
import static fj.Function.curry;
import fj.P2;
import fj.Unit;
import fj.data.Option;
import static fj.data.Option.none;
import static fj.data.Option.some;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.codehaus.httpcache4j.Challenge;
import org.codehaus.httpcache4j.ChallengeMethod;
import org.codehaus.httpcache4j.HTTPMethod;
import static org.codehaus.httpcache4j.HTTPMethod.GET;
import static org.codehaus.httpcache4j.HTTPMethod.POST;
import static org.codehaus.httpcache4j.HTTPMethod.PUT;
import org.codehaus.httpcache4j.HTTPRequest;
import org.codehaus.httpcache4j.HTTPResponse;
import org.codehaus.httpcache4j.MIMEType;
import org.codehaus.httpcache4j.Parameter;
import org.codehaus.httpcache4j.Status;
import org.codehaus.httpcache4j.cache.HTTPCache;
import org.codehaus.httpcache4j.payload.InputStreamPayload;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.events.StartElement;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPath;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
//TODO: This client knows way too much about the server. RESTFUL clients are stupid.
//TODO: we should get the WADL and analyze that for POST URIS.
//TODO: Each resource should then add a Link header for discovering how they can be used.
//TODO: Each resource should also tell the client what can be used with it (the available methods... PUT/POST/DELETE/GET)
public class RestletEmsV1Client implements EmsV1Client {

    private static final String contextPath = EventV1.class.getPackage().getName();

    private static final MIMEType APPLICATION_XML = MIMEType.valueOf("application/xml");

    public final Option<Challenge> challenge;
    public final String baseUri;
    private URI wadlURI;

    private final HTTPCache client;
    private final String getPeopleUrl;
    private final String getPersonUrl;
    private final String getEventsUrl;
    private final String getEventUrl;
    private final String eventsSessionsUrl;
    private final String getSessionUrl;
    private final String findSessionsByDateUrl;
    private final String findSessionsByTitleUrl;
    private final String searchSessionsUrl;
    private final String eventsRoomsUrl;
    private final DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("yyyy/MM/dd");
    private final Marshaller marshaller;
    public final Unmarshaller unmarshaller;

    public RestletEmsV1Client(final HTTPCache client, final String baseUri) {
        this(client, baseUri, Option.<P2<String, String>>none());
    }

    public RestletEmsV1Client(final HTTPCache client, final String baseUri, Option<P2<String, String>> credentials) {
        Validate.noNullElements(new Object[]{client, baseUri, credentials});
        this.client = client;
        this.baseUri = baseUri;

        challenge = credentials.map(newChallenge);
        getPeopleUrl = baseUri + "/1/people";
        getPersonUrl = baseUri + "/1/people/%s";
        getEventsUrl = baseUri + "/1/events";
        getEventUrl = baseUri + "/1/events/%s";
        eventsSessionsUrl = baseUri + "/1/events/%s/sessions";
        getSessionUrl = baseUri + "/1/events/%s/sessions/%s";
        findSessionsByDateUrl = baseUri + "/1/events/%s/sessions/by-date/%s";
        findSessionsByTitleUrl = baseUri + "/1/events/%s/sessions/by-title/%s";
        searchSessionsUrl = baseUri + "/1/events/%s/sessions/search";
        eventsRoomsUrl = baseUri + "/1/events/%s/rooms";
        try {
            // TODO: add schema
            JAXBContext context = JAXBContext.newInstance(contextPath);
            marshaller = context.createMarshaller();
            unmarshaller = context.createUnmarshaller();
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to create marshaller or unmarshaller", e);
        }

        // This has to be here, javac complains that 'challenge' might not be initialized
        defaultProcessRequestForGet = compose(
            curry(setChallenge, challenge),
            curry(setMediaTypes, APPLICATION_XML));

        defaultProcessRequestForAdd = compose(
            curry(setChallenge, challenge),
            curry(setMediaTypes, APPLICATION_XML));
    }

    public void login(URI endpoint) throws Exception {
        HTTPResponse response = client.doCachedRequest(new HTTPRequest(endpoint));
        InputStream stream = response.getPayload().getInputStream();

    }

    // -----------------------------------------------------------------------
    // EmsV1Client Implementation
    // -----------------------------------------------------------------------


    public Option<EventV1> getEvent(String eventId) {
        return defaultGetRequest(eventUnmarshaller, GET, getEventUrl, eventId);
    }

    public URI addEvent(EventV1 event) {
        return request(doCachedRequest,
            compose(curry(setBody(EventV1.class, "event"), event), defaultProcessRequestForAdd),
            defaultProcessResponseForAdd,
            responseToUri,
            POST, getEventUrl);
    }

    public EventListV1 getEvents() {
        return defaultGetListRequest(eventListUnmarshaller, GET, getEventsUrl);
    }

    public SessionListV1 getSessions(String eventId) {
        return defaultGetListRequest(sessionListUnmarshaller, GET, eventsSessionsUrl, eventId);
    }

    public Option<SessionV1> getSession(String eventId, String sessionId) {
        return defaultGetRequest(sessionUnmarshaller, GET, getSessionUrl, eventId, sessionId);
    }

    public SessionListV1 findSessionsByDate(String eventId, LocalDate date) {
        return defaultGetListRequest(sessionListUnmarshaller, GET, findSessionsByDateUrl, eventId, dateFormatter.print(date));
    }

    public SessionListV1 findSessionsByTitle(String eventId, String title) {
        return defaultGetListRequest(sessionListUnmarshaller, GET, findSessionsByTitleUrl, eventId, encode(title));
    }

    public SessionListV1 getSessionsByTitle(String eventId, String title) {
        return defaultGetListRequest(sessionListUnmarshaller, GET, getSessionUrl, eventId, title);
    }

    public SessionListV1 searchForSessions(String eventId, String query) {
        List<Parameter> parameters = new ArrayList<Parameter>();
        parameters.add(new Parameter("q", query));

        return request(doCachedRequest,
            compose(defaultProcessRequestForGet, curry(setParameters, parameters)),
            defaultProcessResponseForGetList,
            sessionListUnmarshaller,
            GET, searchSessionsUrl, eventId);
    }

    public URI addSession(SessionV1 session) {
        return request(doCachedRequest,
            compose(curry(setBody(SessionV1.class, "session"), session),defaultProcessRequestForAdd),
            defaultProcessResponseForAdd,
            responseToUri,
            POST, eventsSessionsUrl, session.getEventUuid());
    }

    public Unit updateSession(SessionV1 session) {
        return request(doCachedRequest,
            compose(curry(setBody(SessionV1.class, "session"), session), defaultProcessRequestForAdd),
            defaultProcessResponseForPUT,
            responseForPut,
            PUT, getSessionUrl, session.getEventUuid(), session.getUuid());
    }

    public URI addRoom(String eventId, RoomV1 room) {
        return request(doCachedRequest,
            compose(curry(setBody(RoomV1.class, "room"), room),defaultProcessRequestForAdd),
            defaultProcessResponseForAdd,
            responseToUri,
            POST, eventsRoomsUrl, eventId);
    }

    public PersonListV1 getPeople() {
        return defaultGetListRequest(personListUnmarshaller, GET, getPeopleUrl);
    }

    public Option<PersonV1> getPerson(String personId) {
        return defaultGetRequest(personUnmarshaller, GET, getPersonUrl, personId);
    }

    public URI addPerson(PersonV1 person) {
        return request(doCachedRequest,
            compose(curry(setBody(PersonV1.class, "person"), person), defaultProcessRequestForAdd),
            defaultProcessResponseForAdd,
            responseToUri,
            POST, getPeopleUrl);
    }

    public Unit updatePerson(PersonV1 person) {
        return request(doCachedRequest,
            compose(curry(setBody(PersonV1.class, "person"), person), defaultProcessRequestForAdd),
            defaultProcessResponseForAdd,
            responseForPut,
            PUT, getPersonUrl, person.getUuid());
    }

    // -----------------------------------------------------------------------
    // Marshallers and unmarshallers
    // -----------------------------------------------------------------------

    public final F<HTTPResponse, Option<EventV1>> eventUnmarshaller = unmarshallToOption(EventV1.class);
    public final F<HTTPResponse, EventListV1> eventListUnmarshaller = unmarshall(EventListV1.class);
    public final F<HTTPResponse, Option<SessionV1>> sessionUnmarshaller = unmarshallToOption(SessionV1.class);
    public final F<HTTPResponse, SessionListV1> sessionListUnmarshaller = unmarshall(SessionListV1.class);
    public final F<HTTPResponse, Option<PersonV1>> personUnmarshaller = unmarshallToOption(PersonV1.class);
    public final F<HTTPResponse, PersonListV1> personListUnmarshaller = unmarshall(PersonListV1.class);

    // -----------------------------------------------------------------------
    // Misc strategies used for with request()
    // -----------------------------------------------------------------------

    /**
     * A default strategy for processing requests. Sets the challenge and
     */
    private final F<HTTPRequest, HTTPRequest> defaultProcessRequestForGet;

    private final F<HTTPRequest, HTTPRequest> defaultProcessRequestForAdd;

    private static final F<HTTPResponse, HTTPResponse> defaultProcessResponseForAdd = new F<HTTPResponse, HTTPResponse>() {
        public HTTPResponse f(HTTPResponse request) {
            // TODO: assert 201 CREATED
            return request;
        }
    };

    private static final F<HTTPResponse, HTTPResponse> defaultProcessResponseForGet = new F<HTTPResponse, HTTPResponse>() {
        public HTTPResponse f(HTTPResponse response) {
            if (response.getStatus().equals(Status.NOT_FOUND) || response.getStatus().equals(Status.OK)) {
                return response;
            }

            throw new RuntimeException("Got unexpected status: " + response.getStatus().getDescription());
        }
    };

    /**
     * Assert that the result was OK
     */
    private static final F<HTTPResponse, HTTPResponse> defaultProcessResponseForGetList = new F<HTTPResponse, HTTPResponse>() {
        public HTTPResponse f(HTTPResponse response) {
            if (response.getStatus().equals(Status.OK)) {
                return response;
            }

            throw new RuntimeException("Got unexpected status: " + response.getStatus().getDescription());
        }
    };

    private static final F<HTTPResponse, URI> responseToUri = new F<HTTPResponse, URI>() {
        public URI f(HTTPResponse response) {
            if (response.getStatus().equals(Status.CREATED)) {
                return URI.create(response.getHeaders().getFirstHeader("Location").getValue());
            }

            throw new RuntimeException("Expected HTTP code 'created', got " + response.getStatus().getCode() + ".");
        }
    };

    private static final F<HTTPResponse, HTTPResponse> defaultProcessResponseForPUT = new F<HTTPResponse, HTTPResponse>() {
        public HTTPResponse f(HTTPResponse response) {
            if(response.getStatus().equals(Status.OK)) {
                return response;
            }

            throw new RuntimeException("Got unexpected status: " + response.getStatus().getDescription());
        }
    };

    private static final F<HTTPResponse, Unit> responseForPut = new F<HTTPResponse, Unit>() {
        public Unit f(HTTPResponse response) {
            if(response.getStatus().equals(Status.OK)) {
                return Unit.unit();
            }

            throw new RuntimeException("Got unexpected status: " + response.getStatus().getDescription());
        }
    };

    private final static F2<Option<Challenge>, HTTPRequest, HTTPRequest> setChallenge = new F2<Option<Challenge>, HTTPRequest, HTTPRequest>() {
        public HTTPRequest f(Option<Challenge> option, HTTPRequest request) {
            if (option.isSome()) {
                Challenge challenge = option.some();
                request.setChallenge(challenge);
                System.out.println(request.getMethod() + " (as '" + challenge.getIdentifier() + "'): " + request.getMethod().toString() + " " + request.getRequestURI().getPath());
            } else {
                System.out.println(request.getMethod() + " (anonymously): " + request.getRequestURI().getPath());
            }

            return request;
        }
    };

    private static F2<MIMEType, HTTPRequest, HTTPRequest> setMediaTypes = new F2<MIMEType, HTTPRequest, HTTPRequest>() {
        public HTTPRequest f(MIMEType preference, HTTPRequest request) {
            request.getPreferences().addMIMEType(preference);
            return request;
        }
    };

    private static F2<List<Parameter>, HTTPRequest, HTTPRequest> setParameters = new F2<List<Parameter>, HTTPRequest, HTTPRequest>() {
        public HTTPRequest f(List<Parameter> parameters, HTTPRequest request) {
            for (Parameter parameter : parameters) {
                request.addParameter(parameter);
            }
            return request;
        }
    };

    private <A> F2<A, HTTPRequest, HTTPRequest> setBody(final Class<A> klass, final String tag) {
        return new F2<A, HTTPRequest, HTTPRequest>() {
            public HTTPRequest f(final A a, HTTPRequest request) {
                try {
                    // TODO: This should be streamed

                    ByteArrayOutputStream data = new ByteArrayOutputStream(1024 * 1024);

                    marshaller.marshal(new JAXBElement<A>(new QName(tag), klass, a), data);

                    System.out.println("-----------------------------------");
                    System.out.write(data.toByteArray());
                    System.out.println();
                    System.out.println("-----------------------------------");

                    request.setPayload(new InputStreamPayload(new ByteArrayInputStream(data.toByteArray()), APPLICATION_XML));

                    return request;
                } catch (JAXBException e) {
                    throw new RuntimeException("Unable to marshall object.", e);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to marshall object.", e);
                }
            }
        };
    }

    private F<HTTPRequest, HTTPResponse> doCachedRequest = new F<HTTPRequest, HTTPResponse>() {
        public HTTPResponse f(HTTPRequest request) {
            return client.doCachedRequest(request);
        }
    };

    // -----------------------------------------------------------------------
    //
    // -----------------------------------------------------------------------

    public <A> F<HTTPResponse, A> unmarshall(final Class<A> klass) {
        return new F<HTTPResponse, A>() {
            public A f(HTTPResponse response) {
                return doUnmarshall(response, klass);
            }
        };
    }

    public <A> F<InputStream, A> unmarshallInputStream(final Class<A> klass) {
        return new F<InputStream, A>() {
            public A f(InputStream inputStream) {
                return doUnmarshallInputStream(klass, inputStream);
            }
        };
    }

    public <A> F<HTTPResponse, Option<A>> unmarshallToOption(final Class<A> klass) {
        return new F<HTTPResponse, Option<A>>() {
            public Option<A> f(HTTPResponse response) {

                if (response.getStatus().equals(Status.NOT_FOUND)) {
                    return none();
                }

                return some(doUnmarshall(response, klass));
            }
        };
    }

    private <A> A doUnmarshall(HTTPResponse response, Class<A> klass) {
        InputStream inputStream = null;

        try {
            inputStream = response.getPayload().getInputStream();
            return doUnmarshallInputStream(klass, inputStream);
        }
        finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private <A> A doUnmarshallInputStream(Class<A> klass, InputStream inputStream) {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();

            /*try {
                IOUtils.copy(inputStream, data);
                System.out.println("GOT:");
                System.out.println("-----------------------------------");
                System.out.write(data.toByteArray());
                System.out.println("-----------------------------------");
                inputStream = new ByteArrayInputStream(data.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } */

            return unmarshaller.unmarshal(new StreamSource(inputStream), klass).getValue();
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to unmarshall.", e);
        }
    }

    private <A> A defaultGetRequest(F<HTTPResponse, A> convertResponse,
                                    HTTPMethod method, String urlTemplate, String... arguments) {
        return request(doCachedRequest,
            defaultProcessRequestForGet,
            defaultProcessResponseForGet,
            convertResponse,
            method, urlTemplate, arguments);
    }

    private <A> A defaultGetListRequest(F<HTTPResponse, A> convertResponse,
                                        HTTPMethod method, String urlTemplate, String... arguments) {
        return request(doCachedRequest,
            defaultProcessRequestForGet,
            defaultProcessResponseForGetList,
            convertResponse,
            method, urlTemplate, arguments);
    }

    private <A> A request(F<HTTPRequest, HTTPResponse> doRequest,
                          F<HTTPRequest, HTTPRequest> processRequest,
                          F<HTTPResponse, HTTPResponse> processResponse,
                          F<HTTPResponse, A> convertResponse,
                          HTTPMethod method, String urlTemplate, String... arguments) {
        URI uri = URI.create(String.format(urlTemplate, (Object[]) arguments));

        return some(new HTTPRequest(uri, method)).
            map(processRequest).
            map(doRequest).
            map(processResponse).
            map(convertResponse).some();
    }

    F<P2<String, String>, Challenge> newChallenge = new F<P2<String, String>, Challenge>() {
        public Challenge f(P2<String, String> credentials) {
            return new Challenge(credentials._1(), credentials._2().toCharArray(), ChallengeMethod.BASIC);
        }
    };

    protected static String encode(String string) {
        if (string == null) {
            return null;
        }

        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (
            UnsupportedEncodingException e) {
            throw new RuntimeException("Unable to decode parameter", e);
        }
    }
}
