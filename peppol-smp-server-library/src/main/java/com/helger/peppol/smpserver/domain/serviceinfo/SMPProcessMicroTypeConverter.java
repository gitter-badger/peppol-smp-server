/**
 * Copyright (C) 2015-2016 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.peppol.smpserver.domain.serviceinfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.collection.ext.CommonsArrayList;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.peppol.identifier.generic.process.SimpleProcessIdentifier;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.MicroElement;
import com.helger.xml.microdom.convert.IMicroTypeConverter;
import com.helger.xml.microdom.convert.MicroTypeConverter;
import com.helger.xml.microdom.util.MicroHelper;

/**
 * This class is internally used to convert {@link SMPProcess} from and to XML.
 *
 * @author Philip Helger
 */
public final class SMPProcessMicroTypeConverter implements IMicroTypeConverter
{
  private static final String ELEMENT_PROCESS_IDENTIFIER = "processidentifier";
  private static final String ELEMENT_ENDPOINT = "endpoint";
  private static final String ELEMENT_EXTENSION = "extension";

  @Nonnull
  public IMicroElement convertToMicroElement (@Nonnull final Object aObject,
                                              @Nullable final String sNamespaceURI,
                                              @Nonnull @Nonempty final String sTagName)
  {
    final ISMPProcess aValue = (ISMPProcess) aObject;
    final IMicroElement aElement = new MicroElement (sNamespaceURI, sTagName);
    aElement.appendChild (MicroTypeConverter.convertToMicroElement (aValue.getProcessIdentifier (),
                                                                    sNamespaceURI,
                                                                    ELEMENT_PROCESS_IDENTIFIER));
    for (final ISMPEndpoint aEndpoint : aValue.getAllEndpoints ())
      aElement.appendChild (MicroTypeConverter.convertToMicroElement (aEndpoint, sNamespaceURI, ELEMENT_ENDPOINT));
    if (aValue.hasExtension ())
      aElement.appendElement (sNamespaceURI, ELEMENT_EXTENSION).appendText (aValue.getExtensionAsString ());
    return aElement;
  }

  @Nonnull
  public ISMPProcess convertToNative (@Nonnull final IMicroElement aElement)
  {
    final SimpleProcessIdentifier aProcessIdentifier = MicroTypeConverter.convertToNative (aElement.getFirstChildElement (ELEMENT_PROCESS_IDENTIFIER),
                                                                                           SimpleProcessIdentifier.class);
    final ICommonsList <SMPEndpoint> aEndpoints = new CommonsArrayList <> ();
    for (final IMicroElement aEndpoint : aElement.getAllChildElements (ELEMENT_ENDPOINT))
      aEndpoints.add (MicroTypeConverter.convertToNative (aEndpoint, SMPEndpoint.class));
    final String sExtension = MicroHelper.getChildTextContentTrimmed (aElement, ELEMENT_EXTENSION);

    return new SMPProcess (aProcessIdentifier, aEndpoints, sExtension);
  }
}
