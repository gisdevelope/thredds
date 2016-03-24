/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.util.net;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import ucar.httpservices.HTTPFactory;
import ucar.httpservices.HTTPMethod;
import ucar.httpservices.HTTPSession;
import ucar.nc2.util.UnitTestCommon;
import ucar.unidata.test.util.NeedsExternalResource;
import ucar.unidata.test.util.TestDir;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * This test is to check non-ssh related authorization: Basic primarily.
 * Notes:
 * 1. If remotetestserver is not localhost, then we disable the
 * credentials call test. This is because e.g. remotetest.unidata.ucar.edu
 * is set up to use redirection on ../restrict/..., so the credentials
 * provider will be called twice. On localhost (using e.g. Intellij or
 * pure Tomcat) the provider will be called once.
 */

@Category(NeedsExternalResource.class)
public class TestAuth extends UnitTestCommon
{
    static final String BADPASSWORD = "bad";

    static protected final String MODULE = "httpclient";

    static protected final boolean IGNORE = true;

    static protected final int[] OKCODES = new int[]{200, 404};

    /**
     * Temporary data directory (for writing temporary data).
     */
    static public String TEMPROOT = "target/test/tmp/"; // relative to module root

    // Add a temporary control for remote versus localhost
    static boolean remote = false;

    static protected class Result
    {
        int status = 0;
        byte[] contents = null;

        public String toString()
        {
            StringBuilder b = new StringBuilder();
            b.append("{");
            b.append("status=");
            b.append(status);
            b.append(", |contents|=");
            b.append(contents == null ? 0 : contents.length);
            b.append("}");
            return b.toString();
        }
    }


    //////////////////////////////////////////////////
    // Provide a non-interactive TestProvider to hold
    // the user+pwd; used in several places
    static class TestProvider implements CredentialsProvider, Serializable
    {
        String username = null;
        String password = null;
        int counter = 0;

        public TestProvider(String username, String password)
        {
            setUserPwd(username, password);
        }

        public void setUserPwd(String username, String password)
        {
            this.username = username;
            this.password = password;
        }

        // Credentials Provider Interface
        public Credentials
        getCredentials(AuthScope scope) //AuthScheme authscheme, String host, int port, boolean isproxy)
        {
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);
            System.err.printf("TestCredentials.getCredentials called: creds=|%s| host=%s port=%d%n",
                    creds.toString(), scope.getHost(), scope.getPort());
            this.counter++;
            return creds;
        }

        public void setCredentials(AuthScope scope, Credentials creds)
        {
            throw new UnsupportedOperationException();
        }

        public void clear()
        {
            throw new UnsupportedOperationException();
        }

        // Serializable Interface
        private void writeObject(java.io.ObjectOutputStream oos)
                throws IOException
        {
            oos.writeObject(this.username);
            oos.writeObject(this.password);
        }

        private void readObject(java.io.ObjectInputStream ois)
                throws IOException, ClassNotFoundException
        {
            this.username = (String) ois.readObject();
            this.password = (String) ois.readObject();
        }
    }

    //////////////////////////////////////////////////

    // Define the test sets

    protected String datadir = null;
    protected String threddsroot = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public TestAuth()
    {
        super("TestAuth");
        setTitle("DAP Authorization tests");
        //HTTPSession.debugHeaders(true);
        HTTPSession.TESTING = true;
        // See if TestDir.remoteTestServer is localhost
        String server = TestDir.remoteTestServer;
        this.remote = !server.startsWith("localhost");
        defineTestCases();
    }

    //////////////////////////////////////////////////

    static class AuthDataBasic
    {
        String url;
        String user = null;
        String password = null;

        public AuthDataBasic(String url, String usr, String pwd)
        {
            this.url = url;
            this.user = usr;
            this.password = pwd;
        }

        public String toString()
        {
            StringBuilder b = new StringBuilder();
            b.append("{");
            b.append("url=|");
            b.append(url);
            b.append("|");
            b.append(", userpwd=");
            b.append(user);
            b.append(":");
            b.append(password);
            b.append("}");
            return b.toString();
        }

        /**
         * Return url with embedded user+pwd
         */
        public String inline()
                throws Exception
        {
            StringBuilder buf = new StringBuilder(this.url);
            int pos = buf.indexOf("://");
            pos += 3;
            buf.insert(pos, "@");
            buf.insert(pos, this.password);
            buf.insert(pos, ":");
            buf.insert(pos, this.user);
            return buf.toString();
        }
    }

    protected List<AuthDataBasic> basictests = new ArrayList<>();


    protected void
    defineTestCases()
    {
        if(this.remote) {
            basictests.add(new AuthDataBasic("http://" + TestDir.remoteTestServer + "/thredds/dodsC/restrict/testData.nc.dds",
                    "tiggeUser", "tigge"));
        } else {
            basictests.add(new AuthDataBasic("" +
                    "http://" + TestDir.remoteTestServer + "/thredds/dodsC/testRestrictedDataset/testData2.nc.dds",
                    "tiggeUser", "tigge"));
        }
    }

    @Test
    public void
    testBasic() throws Exception
    {
        System.out.println("*** Testing: Http Basic Password Authorization");
        for(AuthDataBasic data : basictests) {
            Result result = null;
            TestProvider provider = null;
            System.out.println("Test global credentials provider");
            System.out.println("*** URL: " + data.url);

            provider = new TestProvider(data.user, data.password);

            // Test global credentials provider
            HTTPSession.setGlobalCredentialsProvider(provider);
            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                result = invoke(session, data.url);
            }
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status));
            Assert.assertTrue("no content", result.contents.length > 0);
            Assert.assertTrue("Credentials provider called: " + provider.counter, provider.counter == 1);
        }

        for(AuthDataBasic data : basictests) {
            Result result = null;
            TestProvider provider = null;
            System.out.println("Test local credentials provider");
            System.out.println("*** URL: " + data.url);

            provider = new TestProvider(data.user, data.password);

            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                session.setCredentialsProvider(provider);
                result = invoke(session, data.url);
            }
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status)); // non-existence is ok
            Assert.assertTrue("no content", result.contents.length > 0);
            Assert.assertTrue("Credentials provider called: " + provider.counter, provider.counter == 1);
        }
    }

    @Test
    public void
    testInline() throws Exception
    {
        System.out.println("*** Testing: Http Basic Password Authorization inline in URL");
        for(AuthDataBasic data : basictests) {
            Result result = null;
            System.out.println("*** URL: " + data.inline());
            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                result = invoke(session, data.inline());
            }
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status)); // non-existence is ok
            Assert.assertTrue("no content", result.contents.length > 0);
        }
    }

    @Test
    public void
    testBasicDirect() throws Exception
    {
        System.out.println("*** Testing: Http Basic Password Authorization Using Constant Credentials");
        for(AuthDataBasic data : basictests) {
            Result result = null;
            Credentials creds = new UsernamePasswordCredentials(data.user, data.password);
            System.out.println("Test global credentials");
            System.out.println("*** URL: " + data.url);

            // Test global credentials provider
            HTTPSession.setGlobalCredentials(creds);
            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                result = invoke(session, data.url);
            }
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status)); // non-existence is ok
            Assert.assertTrue("no content", result.contents.length > 0);
        }

        for(AuthDataBasic data : basictests) {
            Result result = null;
            Credentials creds = new UsernamePasswordCredentials(data.user, data.password);
            System.out.println("Test local credentials");
            System.out.println("*** URL: " + data.url);

            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                session.setCredentials(creds);
                result = invoke(session, data.url);

            }
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status)); // non-existence is ok
            Assert.assertTrue("no content", result.contents.length > 0);
        }
    }

    @Test
    public void
    testCache() throws Exception
    {
        System.err.println("*** Testing: Cache Invalidation");
        for(AuthDataBasic data : basictests) {
            Result result = null;
            TestProvider provider = null;
            System.out.println("*** URL: " + data.url);

            // Do each test with a bad password to cause cache invalidation
            provider = new TestProvider(data.user, BADPASSWORD);

            HTTPSession session = HTTPFactory.newSession(data.url);
            session.setCredentialsProvider(provider);
            result = invoke(session, data.url);
            Assert.assertTrue("Incorrect return code: " + result.status, result.status == 401);
            Assert.assertTrue("Credentials provider called: " + provider.counter, provider.counter == 1);

            // retry with correct password;
            // AuthCache should automatically clear bad one from cache.
            provider.setUserPwd(data.user, data.password);
            session.setCredentialsProvider(provider);
            result = invoke(session, data.url);
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status));
            session.close();
        }
    }

    @Test
    public void
    testCache2() throws Exception
    {
        System.err.println("*** Testing: Cache Invalidation visually");
        if(this.remote) {
            System.err.println("Test aborted: requires localhost");
            return;
        }
        for(AuthDataBasic data : basictests) {
            Result result = null;
            Login cp = new Login(data.user, data.password);
            System.out.println("*** URL: " + data.url);
            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                session.setCredentialsProvider(cp);
                result = invoke(session, data.url);
            }
            Assert.assertTrue("Incorrect return code: " + result.status, result.status == 401);
            // retry with correct password;
            // AuthCache should automatically clear bad one from cache.
            try (HTTPSession session = HTTPFactory.newSession(data.url)) {
                session.setCredentialsProvider(cp);
                result = invoke(session, data.url);
            }
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status));
        }
    }


/*
    // This test actually is does nothing because I have no way to test it
    // since it requires a firwall proxy that requires username+pwd
    @Test
    public void
    testFirewall() throws Exception
    {
        if(IGNORE) return; //ignore
        String user = null;
        String pwd = null;
        String host = null;
        int port = -1;
        String url = null;

        Counter counter = new Counter();
        System.err.println("*** Testing: Http Firewall Proxy (with authentication)");
        provider.setPWD(user, pwd);
        System.err.println("*** URL: " + url);
        // Test local credentials provider
        try (HTTPSession session = HTTPFactory.newSession(url);
             HTTPMethod method = HTTPFactory.Get(session, url)) {
            session.setProxy(host, port);
            session.setCredentialsProvider(url, provider);
            int status = method.execute();
            System.err.printf("\tlocal provider: status code = %d\n", status);
            System.err.flush();
            Assert.assertTrue("Incorrect return code: " + result.status, check(result.status));
            // Test global credentials provider
            HTTPSession.setGlobalProxy(host, port);
            HTTPSession.setGlobalTestProvider(url, provider);
            try (HTTPSession session = HTTPFactory.newSession(url);
                 HTTPMethod method = HTTPFactory.Get(session, url)) {
                int status = method.execute();
                Assert.assertTrue("Incorrect return code: " + result.status, check(result.status));
            }
        }
    }
    */

    //////////////////////////////////////////////////

    protected Result
    invoke(HTTPSession session, String url)
            throws IOException
    {
        Result result = new Result();
        try {
            try (HTTPMethod method = HTTPFactory.Get(session, url)) {
                result.status = method.execute();
                System.err.printf("\tglobal provider: status code = %d\n", result.status);
                //System.err.printf("\t|cache| = %d\n", HTTPCachingProvider.getCache().size());
                // try to read in the content
                result.contents = readbinaryfile(method.getResponseAsStream());
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IOException(t);
        }
        return result;
    }

    protected boolean
    check(int code)
    {
        for(int okcode : OKCODES) {
            if(okcode == code) return true;
        }
        return false;
    }
}

/*
javax.net.debug=cmd

where cmd can be:

all            turn on all debugging
ssl            turn on ssl debugging

or

javax.net.debug=ssl:<option>:<option>...

where <option> is taken from the following:

The following can be used with ssl:
	record       enable per-record tracing
	handshake    print each handshake message
	keygen       print key generation data
	session      print session activity
	defaultctx   print default SSL initialization
	sslctx       print SSLContext tracing
	sessioncache print session cache tracing
	keymanager   print key manager tracing
	trustmanager print trust manager tracing
	pluggability print pluggability tracing

	handshake debugging can be widened with:
	data         hex dump of each handshake message
	verbose      verbose handshake message printing

	record debugging can be widened with:
	plaintext    hex dump of record plaintext
	packet       print raw SSL/TLS packets


Process finished with exit code 0
Empty test suite.
*/
