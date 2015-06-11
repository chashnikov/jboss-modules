/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.modules;

import static org.jboss.modules.ModuleXmlParser.endOfDocument;
import static org.jboss.modules.ModuleXmlParser.unexpectedContent;
import static org.jboss.modules.xml.XmlPullParser.END_DOCUMENT;
import static org.jboss.modules.xml.XmlPullParser.END_TAG;
import static org.jboss.modules.xml.XmlPullParser.FEATURE_PROCESS_NAMESPACES;
import static org.jboss.modules.xml.XmlPullParser.START_TAG;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.jboss.modules.xml.MXParser;
import org.jboss.modules.xml.XmlPullParser;
import org.jboss.modules.xml.XmlPullParserException;

/**
 * Helper class to resolve a maven artifact
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @author <a href="mailto:tcerar@redhat.com">Tomaz Cerar</a>
 * @version $Revision: 2 $
 */
class MavenArtifactUtil {

    private static MavenSettings mavenSettings;
    private static final Object settingLoaderMutex = new Object();

    public static MavenSettings getSettings() throws IOException {
        if (mavenSettings != null) {
            return mavenSettings;
        }
        synchronized (settingLoaderMutex) {
            MavenSettings settings = new MavenSettings();

            File m2 = new File(System.getProperty("user.home"), ".m2");
            File settingsPath = new File(m2, "settings.xml");

            if (!settingsPath.exists()) {
                String mavenHome = System.getenv("M2_HOME");
                if (mavenHome != null) {
                    settingsPath = new File(mavenHome, "conf/settings.xml");
                }
            }
            if (settingsPath.exists()) {
                parseSettingsXml(settingsPath, settings);
            }
            if (settings.getLocalRepository() == null) {
                File repository = new File(m2, "repository");
                settings.setLocalRepository(repository);
            }
            settings.resolveActiveSettings();
            mavenSettings = settings;
            return mavenSettings;
        }
    }

    private static MavenSettings parseSettingsXml(File settings, MavenSettings mavenSettings) throws IOException {
        try {
            final MXParser reader = new MXParser();
            reader.setFeature(FEATURE_PROCESS_NAMESPACES, false);
            InputStream source = new BufferedInputStream(new FileInputStream(settings));
            reader.setInput(source, null);
            int eventType;
            while ((eventType = reader.next()) != END_DOCUMENT) {
                switch (eventType) {
                    case START_TAG: {
                        String s = reader.getName();
                        if (s.equals("settings")) {
                            parseSettings(reader, mavenSettings);
                        }
                    }
                    default: {
                        break;
                    }
                }
            }
            return mavenSettings;
        } catch (XmlPullParserException e) {
            throw new IOException("Could not parse maven settings.xml");
        }

    }

    private static void parseSettings(final XmlPullParser reader, MavenSettings mavenSettings) throws XmlPullParserException, IOException {
        int eventType;
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            switch (eventType) {
                case END_TAG: {
                    return;
                }
                case START_TAG: {

                    String s1 = reader.getName();
                    if (s1.equals("localRepository")) {
                        String localRepository = reader.nextText();
                        mavenSettings.setLocalRepository(new File(localRepository));
                        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                            if (eventType == START_TAG) {
                                String s = reader.getName();
                                if (s.equals("profile")) {
                                    parseProfile(reader, mavenSettings);
                                }
                            }
                            else {
                                break;
                            }
                        }
                    }
                    else if (s1.equals("profiles")) {
                        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                            if (eventType == START_TAG) {
                                String s = reader.getName();
                                if (s.equals("profile")) {
                                    parseProfile(reader, mavenSettings);
                                }
                            }
                            else {
                                break;
                            }
                        }
                    }
                    else if (s1.equals("activeProfiles")) {
                        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                            if (eventType == START_TAG) {
                                String s = reader.getName();
                                if (s.equals("activeProfile")) {
                                    mavenSettings.addActiveProfile(reader.nextText());
                                }
                            }
                            else {
                                break;
                            }

                        }
                    }
                    else {
                        skip(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader);
    }

    private static void parseProfile(final XmlPullParser reader, MavenSettings mavenSettings) throws XmlPullParserException, IOException {
        int eventType;
        MavenSettings.Profile profile = new MavenSettings.Profile();
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_TAG) {
                String s1 = reader.getName();
                if (s1.equals("id")) {
                    profile.setId(reader.nextText());
                    while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                        if (eventType == START_TAG) {
                            String s = reader.getName();
                            if (s.equals("repository")) {
                                parseRepository(reader, profile);
                            }
                        }
                        else {
                            break;
                        }

                    }
                }
                else if (s1.equals("repositories")) {
                    while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                        if (eventType == START_TAG) {
                            String s = reader.getName();
                            if (s.equals("repository")) {
                                parseRepository(reader, profile);
                            }
                        }
                        else {
                            break;
                        }

                    }
                }
                else {
                    skip(reader);
                }
            } else {
                break;
            }
        }
        mavenSettings.addProfile(profile);
    }

    private static void parseRepository(final XmlPullParser reader, MavenSettings.Profile profile) throws XmlPullParserException, IOException {
        int eventType;
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_TAG) {
                String s = reader.getName();
                if (s.equals("url")) {
                    profile.addRepository(reader.nextText());
                }
                else {
                    skip(reader);
                }
            } else {
                break;
            }

        }
    }

    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }


    private static final Object artifactLock = new Object();

    /**
     * Tries to find a maven jar artifact from the system property "local.maven.repo.path" This property is a list of
     * platform separated directory names.  If not specified, then it looks in ${user.home}/.m2/repository by default.
     * <p/>
     * If it can't find it in local paths, then will try to download from a remote repository from the system property
     * "remote.maven.repo".  There is no default remote repository.  It will download both the pom and jar and put it
     * into the first directory listed in "local.maven.repo.path" (or the default dir).  This directory will be created
     * if it doesn't exist.
     * <p/>
     * Finally, if you do not want a message to console, then set the system property "maven.download.message" to
     * "false"
     *
     * @param qualifier group:artifact:version[:classifier]
     * @return absolute path to artifact, null if none exists
     * @throws IOException
     */
    public static File resolveJarArtifact(String qualifier) throws IOException {
        String[] split = qualifier.split(":");
        if (split.length < 3) {
            throw new IllegalArgumentException("Illegal artifact " + qualifier);
        }
        String groupId = split[0];
        String artifactId = split[1];
        String version = split[2];
        String classifier = "";
        if (split.length >= 4) { classifier = "-" + split[3]; }

        String artifactRelativePath = relativeArtifactPath(groupId, artifactId, version);
        final MavenSettings settings = getSettings();
        final File localRepository = settings.getLocalRepository();

        // serialize artifact lookup because we want to prevent parallel download
        synchronized (artifactLock) {
            String jarPath = artifactRelativePath + classifier + ".jar";
            File fp = new File(localRepository.toString(), jarPath);
            if (fp.exists()) {
                return fp;
            }

            List<String> remoteRepos = mavenSettings.getRemoteRepositories();
            if (remoteRepos.isEmpty()) {
                return null;
            }

            final File jarFile = new File(localRepository, jarPath);
            final File pomFile = new File(localRepository, artifactRelativePath + ".pom");
            for (String remoteRepository : remoteRepos) {
                try {
                    String remotePomPath = remoteRepository + artifactRelativePath + ".pom";
                    String remoteJarPath = remoteRepository + artifactRelativePath + classifier + ".jar";
                    downloadFile(qualifier + ":pom", remotePomPath, pomFile);
                    downloadFile(qualifier + ":jar", remoteJarPath, jarFile);
                    if (jarFile.exists()) { //download successful
                        return jarFile;
                    }
                } catch (IOException e) {
                    Module.log.trace(e, "Could not download '%s' from '%s' repository", artifactRelativePath, remoteRepository);
                    //
                }
            }
            //could not find it in remote
            Module.log.trace("Could not find in any remote repository");
            return null;
        }
    }

    public static String relativeArtifactPath(String groupId, String artifactId, String version) {
        return relativeArtifactPath(File.separatorChar, groupId, artifactId, version);
    }

    public static String relativeArtifactHttpPath(String groupId, String artifactId, String version) {
        return relativeArtifactPath('/', groupId, artifactId, version);
    }

    private static String relativeArtifactPath(char separator, String groupId, String artifactId, String version) {
        StringBuilder builder = new StringBuilder(groupId.replace('.', separator));
        builder.append(separator).append(artifactId).append(separator).append(version).append(separator).append(artifactId).append('-').append(version);
        return builder.toString();
    }

    public static void downloadFile(String artifact, String src, File dest) throws IOException {
        final URL url = new URL(src);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        boolean message = Boolean.getBoolean("maven.download.message");

        InputStream bis = connection.getInputStream();
        try {
            dest.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(dest);
            try {
                if (message) { System.out.println("Downloading " + artifact); }
                byte[] buf = new byte[16 * 1024];
                int len;
                while ((len = bis.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            } finally {
                StreamUtil.safeClose(fos);
            }
        } finally {
            StreamUtil.safeClose(bis);
        }
    }
}
