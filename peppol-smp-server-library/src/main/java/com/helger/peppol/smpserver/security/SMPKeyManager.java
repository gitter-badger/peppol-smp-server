/**
 * Copyright (C) 2015-2016 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Version: MPL 1.1/EUPL 1.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at:
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Copyright The PEPPOL project (http://www.peppol.eu)
 *
 * Alternatively, the contents of this file may be used under the
 * terms of the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL
 * (the "Licence"); You may not use this work except in compliance
 * with the Licence.
 * You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * If you wish to allow use of your version of this file only
 * under the terms of the EUPL License and not to allow others to use
 * your version of this file under the MPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and
 * other provisions required by the EUPL License. If you do not delete
 * the provisions above, a recipient may use your version of this file
 * under either the MPL or the EUPL License.
 */
package com.helger.peppol.smpserver.security;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.helger.commons.annotation.UsedViaReflection;
import com.helger.commons.collection.ext.CommonsArrayList;
import com.helger.commons.exception.InitializationException;
import com.helger.commons.random.RandomHelper;
import com.helger.commons.scope.singleton.AbstractGlobalSingleton;
import com.helger.commons.ws.TrustManagerTrustAll;
import com.helger.peppol.smpserver.SMPServerConfiguration;
import com.helger.peppol.utils.PeppolKeyStoreHelper;
import com.helger.security.keystore.KeyStoreHelper;
import com.helger.security.keystore.LoadedKey;
import com.helger.security.keystore.LoadedKeyStore;

/**
 * This class holds the private key for signing and the certificate for
 * checking.
 *
 * @author Philip Helger
 */
public final class SMPKeyManager extends AbstractGlobalSingleton
{
  private static final Logger s_aLogger = LoggerFactory.getLogger (SMPKeyManager.class);

  private static final AtomicBoolean s_aCertificateValid = new AtomicBoolean (false);
  private static String s_sInitError;

  private KeyStore m_aKeyStore;
  private KeyStore.PrivateKeyEntry m_aKeyEntry;

  private void _loadCertificates () throws InitializationException
  {
    // Reset every time
    s_aCertificateValid.set (false);
    s_sInitError = null;
    m_aKeyStore = null;
    m_aKeyEntry = null;

    // Load the key store and get the signing key
    final LoadedKeyStore aLoadedKeyStore = KeyStoreHelper.loadKeyStore (SMPServerConfiguration.getKeyStorePath (),
                                                                        SMPServerConfiguration.getKeyStorePassword ());
    if (aLoadedKeyStore.isFailure ())
    {
      s_sInitError = PeppolKeyStoreHelper.getLoadError (aLoadedKeyStore);
      throw new InitializationException (s_sInitError);
    }
    m_aKeyStore = aLoadedKeyStore.getKeyStore ();

    final LoadedKey <KeyStore.PrivateKeyEntry> aLoadedKey = KeyStoreHelper.loadPrivateKey (m_aKeyStore,
                                                                                           SMPServerConfiguration.getKeyStorePath (),
                                                                                           SMPServerConfiguration.getKeyStoreKeyAlias (),
                                                                                           SMPServerConfiguration.getKeyStoreKeyPassword ());
    if (aLoadedKey.isFailure ())
    {
      s_sInitError = PeppolKeyStoreHelper.getLoadError (aLoadedKey);
      throw new InitializationException (s_sInitError);
    }

    m_aKeyEntry = aLoadedKey.getKeyEntry ();
    s_aLogger.info ("SMPKeyManager successfully initialized with keystore '" +
                    SMPServerConfiguration.getKeyStorePath () +
                    "' and alias '" +
                    SMPServerConfiguration.getKeyStoreKeyAlias () +
                    "'");
    s_aCertificateValid.set (true);
  }

  @Deprecated
  @UsedViaReflection
  public SMPKeyManager () throws InitializationException
  {
    _loadCertificates ();
  }

  @Nonnull
  public static SMPKeyManager getInstance ()
  {
    return getGlobalSingleton (SMPKeyManager.class);
  }

  /**
   * @return The configured keystore. May be <code>null</code> if loading
   *         failed. In that case check the result of
   *         {@link #getInitializationError()}.
   */
  @Nullable
  public KeyStore getKeyStore ()
  {
    return m_aKeyStore;
  }

  /**
   * @return The configured private key. May be <code>null</code> if loading
   *         failed. In that case check the result of
   *         {@link #getInitializationError()}.
   */
  @Nullable
  public KeyStore.PrivateKeyEntry getPrivateKeyEntry ()
  {
    return m_aKeyEntry;
  }

  /**
   * Create an SSLContext based on the configured key store and trust store.
   *
   * @return A new {@link SSLContext} and never <code>null</code>.
   * @throws GeneralSecurityException
   *         In case something goes wrong :)
   */
  @Nonnull
  public SSLContext createSSLContext () throws GeneralSecurityException
  {
    // Key manager
    final KeyManagerFactory aKeyManagerFactory = KeyManagerFactory.getInstance (KeyManagerFactory.getDefaultAlgorithm ());
    aKeyManagerFactory.init (getKeyStore (), SMPServerConfiguration.getKeyStoreKeyPassword ());

    // Trust manager
    TrustManager [] aTrustManagers;
    if (SMPTrustManager.isCertificateValid ())
    {
      final TrustManagerFactory aTrustManagerFactory = TrustManagerFactory.getInstance (TrustManagerFactory.getDefaultAlgorithm ());
      aTrustManagerFactory.init (SMPTrustManager.getInstance ().getTrustStore ());
      aTrustManagers = aTrustManagerFactory.getTrustManagers ();
    }
    else
    {
      // No trust store defined
      aTrustManagers = new TrustManager [] { new TrustManagerTrustAll () };
    }

    // Assign key manager and empty trust manager to SSL/TLS context
    final SSLContext aSSLCtx = SSLContext.getInstance ("TLS");
    aSSLCtx.init (aKeyManagerFactory.getKeyManagers (), aTrustManagers, RandomHelper.getSecureRandom ());
    return aSSLCtx;
  }

  public void signXML (@Nonnull final Element aElementToSign,
                       final boolean bBDXR) throws NoSuchAlgorithmException,
                                            InvalidAlgorithmParameterException,
                                            MarshalException,
                                            XMLSignatureException
  {
    // Create a DOM XMLSignatureFactory that will be used to
    // generate the enveloped signature.
    final XMLSignatureFactory aSignatureFactory = XMLSignatureFactory.getInstance ("DOM");

    // Create a Reference to the enveloped document (in this case,
    // you are signing the whole document, so a URI of "" signifies
    // that, and also specify the SHA1 digest algorithm and
    // the ENVELOPED Transform)
    final Reference aReference = aSignatureFactory.newReference ("",
                                                                 aSignatureFactory.newDigestMethod (bBDXR ? DigestMethod.SHA256
                                                                                                          : DigestMethod.SHA1,
                                                                                                    null),
                                                                 new CommonsArrayList<> (aSignatureFactory.newTransform (Transform.ENVELOPED,
                                                                                                                         (TransformParameterSpec) null)),
                                                                 (String) null,
                                                                 (String) null);

    // Create the SignedInfo.
    // XXX should be EXCLUSIVE for PEPPOL but CIPA and this server always used
    // EXCLUSIVE!
    final SignedInfo aSingedInfo = aSignatureFactory.newSignedInfo (aSignatureFactory.newCanonicalizationMethod (bBDXR ? CanonicalizationMethod.INCLUSIVE
                                                                                                                       : CanonicalizationMethod.INCLUSIVE,
                                                                                                                 (C14NMethodParameterSpec) null),
                                                                    aSignatureFactory.newSignatureMethod (bBDXR ? "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"
                                                                                                                : SignatureMethod.RSA_SHA1,
                                                                                                          (SignatureMethodParameterSpec) null),
                                                                    new CommonsArrayList<> (aReference));

    // Create the KeyInfo containing the X509Data.
    final KeyInfoFactory aKeyInfoFactory = aSignatureFactory.getKeyInfoFactory ();
    final X509Certificate aCert = (X509Certificate) m_aKeyEntry.getCertificate ();
    final X509Data aX509Data = aKeyInfoFactory.newX509Data (new CommonsArrayList<> (aCert.getSubjectX500Principal ()
                                                                                         .getName (),
                                                                                    aCert));
    final KeyInfo aKeyInfo = aKeyInfoFactory.newKeyInfo (new CommonsArrayList<> (aX509Data));

    // Create a DOMSignContext and specify the RSA PrivateKey and
    // location of the resulting XMLSignature's parent element.
    final DOMSignContext aSignContext = new DOMSignContext (m_aKeyEntry.getPrivateKey (), aElementToSign);

    // Create the XMLSignature, but don't sign it yet.
    final XMLSignature aSignature = aSignatureFactory.newXMLSignature (aSingedInfo, aKeyInfo);

    // Marshal, generate, and sign the enveloped signature.
    aSignature.sign (aSignContext);
  }

  /**
   * @return A shortcut method to determine if the certification configuration
   *         is valid or not. This method can be used, even if
   *         {@link #getInstance()} throws an exception.
   */
  public static boolean isCertificateValid ()
  {
    return s_aCertificateValid.get ();
  }

  /**
   * If the certificate is not valid according to {@link #isCertificateValid()}
   * this method can be used to determine the error details.
   *
   * @return <code>null</code> if initialization was successful.
   */
  @Nullable
  public static String getInitializationError ()
  {
    return s_sInitError;
  }

  public static void reloadFromConfiguration ()
  {
    try
    {
      final SMPKeyManager aInstance = getGlobalSingletonIfInstantiated (SMPKeyManager.class);
      if (aInstance != null)
        aInstance._loadCertificates ();
      else
        getInstance ();
    }
    catch (final Exception ex)
    {}
  }
}
