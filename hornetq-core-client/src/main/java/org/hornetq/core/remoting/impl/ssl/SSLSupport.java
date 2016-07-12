/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.core.remoting.impl.ssl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.SecureRandom;

import org.hornetq.utils.ClassloadingUtil;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class SSLSupport
{
   // Constants -----------------------------------------------------
   private static String ENABLED_SSL_PROTOCOLS = System.getProperty("org.hornetq.ssl.enabled.protocols", "");

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public static SSLContext createContext(final String keystoreProvider, final String keystorePath, final String keystorePassword,
                                          final String trustStoreProvider, final String trustStorePath, final String trustStorePassword) throws Exception
   {
      SSLContext context = SSLContext.getInstance("TLS");
      KeyManager[] keyManagers = SSLSupport.loadKeyManagers(keystoreProvider, keystorePath, keystorePassword);
      TrustManager[] trustManagers = SSLSupport.loadTrustManager(trustStoreProvider, trustStorePath, trustStorePassword);
      context.init(keyManagers, trustManagers, new SecureRandom());
      return context;
   }

   public static List<String> setEnabledProtocols(SSLEngine engine, String enabledProtocols)
   {
      List<String> disallowedProtocols = new ArrayList<String>();
      List<String> protocols = new ArrayList<String>();
      Set<String> set = new HashSet<String>();
      boolean warnDisallowedProtocol = false;

      if (enabledProtocols.isEmpty())
      {
         enabledProtocols = ENABLED_SSL_PROTOCOLS;
      }

      if (enabledProtocols.isEmpty())
      {
         protocols.addAll(Arrays.asList(engine.getEnabledProtocols()));
      }
      else
      {
         for (String s : enabledProtocols.trim().split(","))
         {
            protocols.add(s.trim());
         }
      }
      for (String s : protocols)
      {
         if (s.equals("SSLv3") || s.equals("SSLv2Hello"))
         {
            disallowedProtocols.add(s);
            continue;
         }
         set.add(s);
      }
      engine.setEnabledProtocols(set.toArray(new String[0]));
      return disallowedProtocols;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private static TrustManager[] loadTrustManager(final String trustStoreProvider,
                                                  final String trustStorePath,
                                                  final String trustStorePassword) throws Exception
   {
      if (trustStorePath == null && ("JKS".equals(trustStoreProvider) || trustStoreProvider == null))
      {
         return null;
      }
      else
      {
         TrustManagerFactory trustMgrFactory;
         KeyStore trustStore = SSLSupport.loadKeystore(trustStoreProvider, trustStorePath, trustStorePassword);
         trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         trustMgrFactory.init(trustStore);
         return trustMgrFactory.getTrustManagers();
      }
   }

   private static KeyStore loadKeystore(final String keystoreProvider, final String keystorePath, final String keystorePassword) throws Exception
   {
      assert keystorePath != null || "JKS".equals(keystoreProvider) == false;
      assert keystorePassword != null;

      KeyStore ks = KeyStore.getInstance(keystoreProvider);
      InputStream in = null;
      try
      {
         if ("JKS".equals(keystoreProvider))
         {
            URL keystoreURL = SSLSupport.validateStoreURL(keystorePath);
            in = keystoreURL.openStream();
         }
         ks.load(in, keystorePassword.toCharArray());
      }
      finally
      {
         if (in != null)
         {
            try
            {
               in.close();
            }
            catch (IOException ignored)
            {
            }
         }
      }
      return ks;
   }

   private static KeyManager[] loadKeyManagers(final String keyStoreProvider, final String keystorePath, final String keystorePassword) throws Exception
   {
      if (keystorePath == null && ("JKS".equals(keyStoreProvider) || keyStoreProvider == null))
      {
         return null;
      }
      else
      {
         KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         KeyStore ks = SSLSupport.loadKeystore(keyStoreProvider, keystorePath, keystorePassword);
         kmf.init(ks, keystorePassword.toCharArray());

         return kmf.getKeyManagers();
      }
   }

   private static URL validateStoreURL(final String storePath) throws Exception
   {
      assert storePath != null;

      // First see if this is a URL
      try
      {
         return new URL(storePath);
      }
      catch (MalformedURLException e)
      {
         File file = new File(storePath);
         if (file.exists() == true && file.isFile())
         {
            return file.toURI().toURL();
         }
         else
         {
            URL url = findResource(storePath);
            if (url != null)
            {
               return url;
            }
         }
      }

      throw new Exception("Failed to find a store at " + storePath);
   }

   /**
    * This seems duplicate code all over the place, but for security reasons we can't let something like this to be open in a
    * utility class, as it would be a door to load anything you like in a safe VM.
    * For that reason any class trying to do a privileged block should do with the AccessController directly.
    */
   private static URL findResource(final String resourceName)
   {
      return AccessController.doPrivileged(new PrivilegedAction<URL>()
      {
         public URL run()
         {
            return ClassloadingUtil.findResource(resourceName);
         }
      });
   }


   // Inner classes -------------------------------------------------
}
