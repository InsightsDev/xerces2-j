/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 1999,2000,2001 The Apache Software Foundation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.xerces.impl.v2;

import org.apache.xerces.impl.v2.datatypes.*;
import org.apache.xerces.impl.v2.identity.*;

import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.validation.ValidationManager;
import org.apache.xerces.impl.XMLErrorReporter;
import org.apache.xerces.impl.msg.XMLMessageFormatter;
import org.apache.xerces.impl.validation.ValidationState;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

import java.io.IOException;
import org.apache.xerces.util.NamespaceSupport;
import org.apache.xerces.util.SymbolTable;
import org.apache.xerces.util.XMLChar;
import org.apache.xerces.util.IntStack;

import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLDocumentHandler;
import org.apache.xerces.xni.XMLDTDHandler;
import org.apache.xerces.xni.XMLDTDContentModelHandler;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLComponent;
import org.apache.xerces.xni.parser.XMLComponentManager;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLDocumentFilter;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * The DTD validator. The validator implements a document
 * filter: receiving document events from the scanner; validating
 * the content and structure; augmenting the InfoSet, if applicable;
 * and notifying the parser of the information resulting from the
 * validation process.
 * <p>
 * This component requires the following features and properties from the
 * component manager that uses it:
 * <ul>
 *  <li>http://xml.org/sax/features/validation</li>
 *  <li>http://apache.org/xml/properties/internal/symbol-table</li>
 *  <li>http://apache.org/xml/properties/internal/error-reporter</li>
 *  <li>http://apache.org/xml/properties/internal/entity-resolver</li>
 * </ul>
 *
 * @author Sandy Gao, IBM
 * @author Elena Litani, IBM
 * @author Eric Ye, IBM
 * @author Andy Clark, IBM
 * @author Jeffrey Rodriguez IBM
 *
 * @version $Id$
 */
public class SchemaValidator
    implements XMLComponent, XMLDocumentFilter,
        FieldActivator // for identity constraints
    {

    //
    // Constants
    //
    private static final boolean DEBUG = false;
    // feature identifiers

    /** Feature identifier: validation. */
    protected static final String VALIDATION =
        Constants.XERCES_FEATURE_PREFIX + Constants.SCHEMA_VALIDATION_FEATURE;

    /** Feature identifier: dynamic validation. */
    protected static final String DYNAMIC_VALIDATION =
        Constants.XERCES_FEATURE_PREFIX + Constants.DYNAMIC_VALIDATION_FEATURE;

    // property identifiers

    /** Property identifier: symbol table. */
    protected static final String SYMBOL_TABLE =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SYMBOL_TABLE_PROPERTY;

    /** Property identifier: error reporter. */
    protected static final String ERROR_REPORTER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY;

    /** Property identifier: entiry resolver. */
    protected static final String ENTITY_RESOLVER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_RESOLVER_PROPERTY;


    protected static final String VALIDATION_MANAGER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.VALIDATION_MANAGER_PROPERTY;
    // REVISIT: this is just a temporary solution for entity resolver
    //          while we are making a decision
    protected static final String ENTITY_MANAGER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_MANAGER_PROPERTY;
    // recognized features and properties

    /** Recognized features. */
    protected static final String[] RECOGNIZED_FEATURES = {
        VALIDATION,
        DYNAMIC_VALIDATION,
    };

    /** Recognized properties. */
    protected static final String[] RECOGNIZED_PROPERTIES = {
        SYMBOL_TABLE,
        ERROR_REPORTER,
        ENTITY_RESOLVER,
        VALIDATION_MANAGER
    };

    //
    // Data
    //

    // features

    /** Validation. */
    protected boolean fValidation = false;
    protected boolean fDynamicValidation = false;
    protected boolean fDoValidation = false;

    // properties

    /** Symbol table. */
    protected SymbolTable fSymbolTable;

    /** Error reporter. */
    protected XMLErrorReporter fErrorReporter;

    /** Entity resolver */
    protected XMLEntityResolver fEntityResolver;


    // updated during reset
    protected ValidationManager fValidationManager = null;
    protected ValidationState fValidationState = null;
    // handlers

    /** Document handler. */
    protected XMLDocumentHandler fDocumentHandler;

    //
    // XMLComponent methods
    //


    /**
     * Returns a list of feature identifiers that are recognized by
     * this component. This method may return null if no features
     * are recognized by this component.
     */
    public String[] getRecognizedFeatures() {
        return RECOGNIZED_FEATURES;
    } // getRecognizedFeatures():String[]

    /**
     * Sets the state of a feature. This method is called by the component
     * manager any time after reset when a feature changes state.
     * <p>
     * <strong>Note:</strong> Components should silently ignore features
     * that do not affect the operation of the component.
     *
     * @param featureId The feature identifier.
     * @param state     The state of the feature.
     *
     * @throws SAXNotRecognizedException The component should not throw
     *                                   this exception.
     * @throws SAXNotSupportedException The component should not throw
     *                                  this exception.
     */
    public void setFeature(String featureId, boolean state)
        throws XMLConfigurationException {
        if (featureId.equals(VALIDATION))
            fValidation = state;
        else if (featureId.equals(DYNAMIC_VALIDATION))
            fDynamicValidation = state;
    } // setFeature(String,boolean)

    /**
     * Returns a list of property identifiers that are recognized by
     * this component. This method may return null if no properties
     * are recognized by this component.
     */
    public String[] getRecognizedProperties() {
        return RECOGNIZED_PROPERTIES;
    } // getRecognizedProperties():String[]

    /**
     * Sets the value of a property. This method is called by the component
     * manager any time after reset when a property changes value.
     * <p>
     * <strong>Note:</strong> Components should silently ignore properties
     * that do not affect the operation of the component.
     *
     * @param propertyId The property identifier.
     * @param value      The value of the property.
     *
     * @throws SAXNotRecognizedException The component should not throw
     *                                   this exception.
     * @throws SAXNotSupportedException The component should not throw
     *                                  this exception.
     */
    public void setProperty(String propertyId, Object value)
        throws XMLConfigurationException {
    } // setProperty(String,Object)

    //
    // XMLDocumentSource methods
    //

    /**
     * Sets the document handler to receive information about the document.
     *
     * @param documentHandler The document handler.
     */
    public void setDocumentHandler(XMLDocumentHandler documentHandler) {
        fDocumentHandler = documentHandler;
    } // setDocumentHandler(XMLDocumentHandler)

    //
    // XMLDocumentHandler methods
    //

    /**
     * The start of the document.
     *
     * @param systemId The system identifier of the entity if the entity
     *                 is external, null otherwise.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal entities or a document entity that is
     *                 parsed from a java.io.Reader).
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startDocument(XMLLocator locator, String encoding)
        throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.startDocument(locator, encoding);
        }
        if(fValidation)
            fValueStoreCache.startDocument();

    } // startDocument(XMLLocator,String)

    /**
     * Notifies of the presence of an XMLDecl line in the document. If
     * present, this method will be called immediately following the
     * startDocument call.
     *
     * @param version    The XML version.
     * @param encoding   The IANA encoding name of the document, or null if
     *                   not specified.
     * @param standalone The standalone value, or null if not specified.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void xmlDecl(String version, String encoding, String standalone)
        throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.xmlDecl(version, encoding, standalone);
        }

    } // xmlDecl(String,String,String)

    /**
     * Notifies of the presence of the DOCTYPE line in the document.
     *
     * @param rootElement The name of the root element.
     * @param publicId    The public identifier if an external DTD or null
     *                    if the external DTD is specified using SYSTEM.
     * @param systemId    The system identifier if an external DTD, null
     *                    otherwise.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void doctypeDecl(String rootElement, String publicId, String systemId)
        throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.doctypeDecl(rootElement, publicId, systemId);
        }

    } // doctypeDecl(String,String,String)

    /**
     * The start of a namespace prefix mapping. This method will only be
     * called when namespace processing is enabled.
     *
     * @param prefix The namespace prefix.
     * @param uri    The URI bound to the prefix.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startPrefixMapping(String prefix, String uri)
        throws XNIException {

        handleStartPrefix(prefix, uri);

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.startPrefixMapping(prefix, uri);
        }

    } // startPrefixMapping(String,String)

    /**
     * The start of an element.
     *
     * @param element    The name of the element.
     * @param attributes The element attributes.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startElement(QName element, XMLAttributes attributes)
        throws XNIException {

        handleStartElement(element, attributes);
        if (fDocumentHandler != null) {
            fDocumentHandler.startElement(element, attributes);
        }

    } // startElement(QName,XMLAttributes)

    /**
     * An empty element.
     *
     * @param element    The name of the element.
     * @param attributes The element attributes.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void emptyElement(QName element, XMLAttributes attributes)
        throws XNIException {

        handleStartElement(element, attributes);
        handleEndElement(element);
        if (fDocumentHandler != null) {
            fDocumentHandler.emptyElement(element, attributes);
        }

    } // emptyElement(QName,XMLAttributes)

    /**
     * Character content.
     *
     * @param text The content.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void characters(XMLString text) throws XNIException {
        if (fSkipValidationDepth >= 0)
            return;
        boolean allWhiteSpace = true;
        for (int i=text.offset; i< text.offset+text.length; i++) {
                if (!XMLChar.isSpace(text.ch[i])) {
                    allWhiteSpace = false;
                    break;
                }
        }

        fBuffer.append(text.toString());
        if (!allWhiteSpace) {
            fSawCharacters = true;
        }


        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.characters(text);
        }

       // call all active identity constraints
       int count = fMatcherStack.getMatcherCount();
       for (int i = 0; i < count; i++) {
           XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
           matcher.characters(text);
       }
    } // characters(XMLString)

    /**
     * Ignorable whitespace. For this method to be called, the document
     * source must have some way of determining that the text containing
     * only whitespace characters should be considered ignorable. For
     * example, the validator can determine if a length of whitespace
     * characters in the document are ignorable based on the element
     * content model.
     *
     * @param text The ignorable whitespace.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void ignorableWhitespace(XMLString text) throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.ignorableWhitespace(text);
        }

       // call all active identity constraints
       int count = fMatcherStack.getMatcherCount();
       for (int i = 0; i < count; i++) {
           XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
           matcher.characters(text);
       }
    } // ignorableWhitespace(XMLString)

    /**
     * The end of an element.
     *
     * @param element The name of the element.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endElement(QName element) throws XNIException {

        handleEndElement(element);
        if (fDocumentHandler != null) {
            fDocumentHandler.endElement(element);
        }

    } // endElement(QName)

    /**
     * The end of a namespace prefix mapping. This method will only be
     * called when namespace processing is enabled.
     *
     * @param prefix The namespace prefix.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endPrefixMapping(String prefix) throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.endPrefixMapping(prefix);
        }

    } // endPrefixMapping(String)

    /**
     * The start of a CDATA section.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startCDATA() throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.startCDATA();
        }

    } // startCDATA()

    /**
     * The end of a CDATA section.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endCDATA() throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.endCDATA();
        }

    } // endCDATA()

    /**
     * The end of the document.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endDocument() throws XNIException {

        // call handlers
        if (fDocumentHandler != null) {
            fDocumentHandler.endDocument();
        }
        if(fValidation)
            fValueStoreCache.endDocument();

    } // endDocument()

    //
    // XMLDocumentHandler and XMLDTDHandler methods
    //

    /**
     * This method notifies of the start of an entity. The DTD has the
     * pseudo-name of "[dtd]; parameter entity names start with '%'; and
     * general entity names are just the entity name.
     * <p>
     * <strong>Note:</strong> Since the DTD is an entity, the handler
     * will be notified of the start of the DTD entity by calling the
     * startEntity method with the entity name "[dtd]" <em>before</em> calling
     * the startDTD method.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param name     The name of the entity.
     * @param publicId The public identifier of the entity if the entity
     *                 is external, null otherwise.
     * @param systemId The system identifier of the entity if the entity
     *                 is external, null otherwise.
     * @param baseSystemId The base system identifier of the entity if
     *                     the entity is external, null otherwise.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal parameter entities).
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startEntity(String name,
                            String publicId, String systemId,
                            String baseSystemId,
                            String encoding) throws XNIException {

        if (fDocumentHandler != null) {
            fDocumentHandler.startEntity(name, publicId, systemId,
                                         baseSystemId, encoding);
        }

    } // startEntity(String,String,String,String,String)

    /**
     * Notifies of the presence of a TextDecl line in an entity. If present,
     * this method will be called immediately following the startEntity call.
     * <p>
     * <strong>Note:</strong> This method will never be called for the
     * document entity; it is only called for external general entities
     * referenced in document content.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param version  The XML version, or null if not specified.
     * @param encoding The IANA encoding name of the entity.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void textDecl(String version, String encoding) throws XNIException {

        if (fDocumentHandler != null) {
            fDocumentHandler.textDecl(version, encoding);
        }

    } // textDecl(String,String)

    /**
     * A comment.
     *
     * @param text The text in the comment.
     *
     * @throws XNIException Thrown by application to signal an error.
     */
    public void comment(XMLString text) throws XNIException {

        if (fDocumentHandler != null) {
            fDocumentHandler.comment(text);
        }

    } // comment(XMLString)

    /**
     * A processing instruction. Processing instructions consist of a
     * target name and, optionally, text data. The data is only meaningful
     * to the application.
     * <p>
     * Typically, a processing instruction's data will contain a series
     * of pseudo-attributes. These pseudo-attributes follow the form of
     * element attributes but are <strong>not</strong> parsed or presented
     * to the application as anything other than text. The application is
     * responsible for parsing the data.
     *
     * @param target The target.
     * @param data   The data or null if none specified.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void processingInstruction(String target, XMLString data)
        throws XNIException {

        if (fDocumentHandler != null) {
            fDocumentHandler.processingInstruction(target, data);
        }

    } // processingInstruction(String,XMLString)

    /**
     * This method notifies the end of an entity. The DTD has the pseudo-name
     * of "[dtd]; parameter entity names start with '%'; and general entity
     * names are just the entity name.
     * <p>
     * <strong>Note:</strong> Since the DTD is an entity, the handler
     * will be notified of the end of the DTD entity by calling the
     * endEntity method with the entity name "[dtd]" <em>after</em> calling
     * the endDTD method.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param name The name of the entity.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endEntity(String name) throws XNIException {

        if (fDocumentHandler != null) {
            fDocumentHandler.endEntity(name);
        }

    } // endEntity(String)

    // constants

    static final int INITIAL_STACK_SIZE = 8;
    static final int INC_STACK_SIZE     = 8;

    // some constants that'll be added into the symbol table
    String XMLNS;
    String URI_XSI;
    String XSI_SCHEMALOCATION;
    String XSI_NONAMESPACESCHEMALOCATION;
    String XSI_TYPE;
    String XSI_NIL;
    String URI_SCHEMAFORSCHEMA;

    //
    // Data
    //

    /** Schema grammar resolver. */
    final XSGrammarResolver fGrammarResolver;
    final SubstitutionGroupHandler fSubGroupHandler;

    /** Schema handler */
    final XSDHandler fSchemaHandler;

    /** Namespace support. */
    final NamespaceSupport fNamespaceSupport = new NamespaceSupport();
    /** this flag is used to indicate whether the next prefix binding
     *  should start a new context (.pushContext)
     */
    boolean fPushForNextBinding;
    /** the DV usd to convert xsi:type to a QName */
    // REVISIT: in new simple type design, make things in DVs static,
    //          so that we can QNameDV.getCompiledForm()
    final DatatypeValidator fQNameDV = (DatatypeValidator)SchemaGrammar.SG_SchemaNS.getGlobalTypeDecl(SchemaSymbols.ATTVAL_QNAME);

    /** used to build content models */
    // REVISIT: create decl pool, and pass it to each traversers
    final CMBuilder fCMBuilder = new CMBuilder(new XSDeclarationPool());

    // state

    /** Skip validation. */
    int fSkipValidationDepth;

    /** Element depth. */
    int fElementDepth;

    /** Child count. */
    int fChildCount;

    /** Element decl stack. */
    int[] fChildCountStack = new int[INITIAL_STACK_SIZE];

    /** Current element declaration. */
    XSElementDecl fCurrentElemDecl;

    /** Element decl stack. */
    XSElementDecl[] fElemDeclStack = new XSElementDecl[INITIAL_STACK_SIZE];

    /** nil value of the current element */
    boolean fNil;

    /** nil value stack */
    boolean[] fNilStack = new boolean[INITIAL_STACK_SIZE];

    /** Current type. */
    XSTypeDecl fCurrentType;

    /** type stack. */
    XSTypeDecl[] fTypeStack = new XSTypeDecl[INITIAL_STACK_SIZE];

    /** Current content model. */
    XSCMValidator fCurrentCM;

    /** Content model stack. */
    XSCMValidator[] fCMStack = new XSCMValidator[INITIAL_STACK_SIZE];

    /** the current state of the current content model */
    int[] fCurrCMState;

    /** stack to hold content model states */
    int[][] fCMStateStack = new int[INITIAL_STACK_SIZE][];

    /** Temporary string buffers. */
    final StringBuffer fBuffer = new StringBuffer();

    /** Did we see non-whitespace character data? */
    boolean fSawCharacters = false;

    /** Stack to record if we saw character data outside of element content*/
    boolean[] fStringContent = new boolean[INITIAL_STACK_SIZE];
    /**
     * This table has to be own by instance of XMLValidator and shared
     * among ID, IDREF and IDREFS.
     * REVISIT: Should replace with a lighter structure.
     */
    final Hashtable fTableOfIDs = new Hashtable();
    final Hashtable fTableOfIDRefs = new Hashtable();

    /**
     * temprory qname
     */
    final QName fTempQName = new QName();
    /**
     * temprory empty object, used to fill IDREF table
     */
    static final Object fTempObject = new Object();

    // identity constraint information

    /**
     * Stack of active XPath matchers for identity constraints. All
     * active XPath matchers are notified of startElement, characters
     * and endElement callbacks in order to perform their matches.
     * <p>
     * For each element with identity constraints, the selector of
     * each identity constraint is activated. When the selector matches
     * its XPath, then all the fields of the identity constraint are
     * activated.
     * <p>
     * <strong>Note:</strong> Once the activation scope is left, the
     * XPath matchers are automatically removed from the stack of
     * active matchers and no longer receive callbacks.
     */
    protected XPathMatcherStack fMatcherStack = new XPathMatcherStack();

    /** Cache of value stores for identity constraint fields. */
    protected ValueStoreCache fValueStoreCache = new ValueStoreCache();

    //
    // Constructors
    //

    /** Default constructor. */
    public SchemaValidator() {

        fGrammarResolver = new XSGrammarResolver();
        fSubGroupHandler = new SubstitutionGroupHandler(fGrammarResolver);
        fSchemaHandler = new XSDHandler(fGrammarResolver, fSubGroupHandler);

    } // <init>()


    /*
     * Resets the component. The component can query the component manager
     * about any features and properties that affect the operation of the
     * component.
     *
     * @param componentManager The component manager.
     *
     * @throws SAXException Thrown by component on finitialization error.
     *                      For example, if a feature or property is
     *                      required for the operation of the component, the
     *                      component manager may throw a
     *                      SAXNotRecognizedException or a
     *                      SAXNotSupportedException.
     */
    public void reset(XMLComponentManager componentManager) throws XMLConfigurationException {

        // get error reporter
        fErrorReporter = (XMLErrorReporter)componentManager.getProperty(ERROR_REPORTER);


        // get symbol table. if it's a new one, add symbols to it.
        SymbolTable symbolTable = (SymbolTable)componentManager.getProperty(SYMBOL_TABLE);
        if (symbolTable != fSymbolTable) {
            XMLNS = symbolTable.addSymbol(SchemaSymbols.O_XMLNS);
            URI_XSI = symbolTable.addSymbol(SchemaSymbols.URI_XSI);
            XSI_SCHEMALOCATION = symbolTable.addSymbol(SchemaSymbols.OXSI_SCHEMALOCATION);
            XSI_NONAMESPACESCHEMALOCATION = symbolTable.addSymbol(SchemaSymbols.OXSI_NONAMESPACESCHEMALOCATION);
            XSI_TYPE = symbolTable.addSymbol(SchemaSymbols.OXSI_TYPE);
            XSI_NIL = symbolTable.addSymbol(SchemaSymbols.OXSI_NIL);
            URI_SCHEMAFORSCHEMA = symbolTable.addSymbol(SchemaSymbols.OURI_SCHEMAFORSCHEMA);
        }
        fSymbolTable = symbolTable;

        // get entity resolver. if there is no one, create a default
        // REVISIT: use default entity resolution from ENTITY MANAGER - temporary solution
        fEntityResolver = (XMLEntityResolver)componentManager.getProperty(ENTITY_MANAGER);

        // initialize namespace support
        fNamespaceSupport.reset(fSymbolTable);
        fPushForNextBinding = true;
        fValidationManager= (ValidationManager)componentManager.getProperty(VALIDATION_MANAGER);
        fValidationManager.reset();

        // clear grammars, and put the one for schema namespace there
        fGrammarResolver.reset();
        fGrammarResolver.putGrammar(URI_SCHEMAFORSCHEMA, SchemaGrammar.SG_SchemaNS);

        // clear thing in substitution group handler
        fSubGroupHandler.reset();

        // reset schema handler and all traversal objects
        fSchemaHandler.reset(fErrorReporter, fEntityResolver, fSymbolTable);

        // initialize state
        fCurrentElemDecl = null;
        fNil = false;
        fCurrentType = null;
        fCurrentCM = null;
        fCurrCMState = null;
        fBuffer.setLength(0);
        fSawCharacters=false;
        fSkipValidationDepth = -1;
        fElementDepth = -1;
        fChildCount = 0;

        // clear values stored in id and idref table
        fTableOfIDs.clear();
        fTableOfIDRefs.clear();

        fMatcherStack.clear();

        fValueStoreCache = new ValueStoreCache();
    } // reset(XMLComponentManager)

    /** ensure element stack capacity */
    void ensureStackCapacity() {

        if (fElementDepth == fElemDeclStack.length) {
            int newSize = fElementDepth + INC_STACK_SIZE;
            int[] newArrayI = new int[newSize];
            System.arraycopy(fChildCountStack, 0, newArrayI, 0, fElementDepth);
            fChildCountStack = newArrayI;

            XSElementDecl[] newArrayE = new XSElementDecl[newSize];
            System.arraycopy(fElemDeclStack, 0, newArrayE, 0, fElementDepth);
            fElemDeclStack = newArrayE;

            boolean[] newArrayB = new boolean[newSize];
            System.arraycopy(fNilStack, 0, newArrayB, 0, fElementDepth);
            fNilStack = newArrayB;

            XSTypeDecl[] newArrayT = new XSTypeDecl[newSize];
            System.arraycopy(fTypeStack, 0, newArrayT, 0, fElementDepth);
            fTypeStack = newArrayT;

            XSCMValidator[] newArrayC = new XSCMValidator[newSize];
            System.arraycopy(fCMStack, 0, newArrayC, 0, fElementDepth);
            fCMStack = newArrayC;

            boolean[] newArrayD = new boolean[newSize];
            System.arraycopy(fStringContent, 0, newArrayD, 0, fElementDepth);
            fStringContent = newArrayD;

            int[][] newArrayIA = new int[newSize][];
            System.arraycopy(fCMStateStack, 0, newArrayIA, 0, fElementDepth);
            fCMStateStack = newArrayIA;
        }

    } // ensureStackCapacity

    //
    // FieldActivator methods
    //

   /**
    * Start the value scope for the specified identity constraint. This
    * method is called when the selector matches in order to initialize
    * the value store.
    *
    * @param identityConstraint The identity constraint.
    */
   public void startValueScopeFor(IdentityConstraint identityConstraint)
        throws XNIException {

        for(int i=0; i<identityConstraint.getFieldCount(); i++) {
            Field field = identityConstraint.getFieldAt(i);
            ValueStoreBase valueStore = fValueStoreCache.getValueStoreFor(field);
            valueStore.startValueScope();
        }

    } // startValueScopeFor(IdentityConstraint identityConstraint)

    /**
     * Request to activate the specified field. This method returns the
     * matcher for the field.
     *
     * @param field The field to activate.
     */
    public XPathMatcher activateField(Field field) throws XNIException {
        ValueStore valueStore = fValueStoreCache.getValueStoreFor(field);
        field.setMayMatch(true);
        XPathMatcher matcher = field.createMatcher(valueStore);
        fMatcherStack.addMatcher(matcher);
        matcher.startDocumentFragment(fSymbolTable);
        return matcher;
    } // activateField(Field):XPathMatcher

    /**
     * Ends the value scope for the specified identity constraint.
     *
     * @param identityConstraint The identity constraint.
     */
    public void endValueScopeFor(IdentityConstraint identityConstraint)
        throws XNIException {

        ValueStoreBase valueStore = fValueStoreCache.getValueStoreFor(identityConstraint);
        valueStore.endValueScope();

    } // endValueScopeFor(IdentityConstraint)

    // a utility method for Idnetity constraints
    private void activateSelectorFor(IdentityConstraint ic) throws XNIException {
        Selector selector = ic.getSelector();
        FieldActivator activator = this;
        if(selector == null)
            return;
        XPathMatcher matcher = selector.createMatcher(activator);
        fMatcherStack.addMatcher(matcher);
        matcher.startDocumentFragment(fSymbolTable);
    }

    //
    // Protected methods
    //

    /** Handle element. */
    void handleStartElement(QName element, XMLAttributes attributes) {
        if (DEBUG) {
            System.out.println("handleStartElement: " +element);
        }


        // we receive prefix binding events before this one,
        // so at this point, the prefix bindings for this element is done,
        // and we need to push context when we receive another prefix binding.

        // but if fPushForNextBinding is still true, that means there has
        // been no prefix binding for this element. we still need to push
        // context, because the context is always popped in end element.
        if (fPushForNextBinding)
            fNamespaceSupport.pushContext();
        else
            fPushForNextBinding = true;

        // whether to do validation
        // root element
        // REVISIT: consider DynamicValidation
        if (fElementDepth == -1) {
            fDoValidation = fValidation;
            fValidationState = fValidationManager.getValidationState();
            fValidationState.setNamespaceSupport(fNamespaceSupport);
            fValidationState.setSymbolTable(fSymbolTable);
        }

        // if we are in the content of "skip", then just skip this element
        // REVISIT:  is this the correct behaviour for ID constraints?  -NG
        if (fSkipValidationDepth >= 0) {
            fElementDepth++;
            return;
        }

        // if it's not the root element, we push the current states in the stacks
        if (fElementDepth != -1) {
            ensureStackCapacity();
            fChildCountStack[fElementDepth] = fChildCount+1;
            fChildCount = 0;
            fElemDeclStack[fElementDepth] = fCurrentElemDecl;
            fNilStack[fElementDepth] = fNil;
            fTypeStack[fElementDepth] = fCurrentType;
            fCMStack[fElementDepth] = fCurrentCM;
            fStringContent[fElementDepth] = fSawCharacters;
        }

        // get xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes,
        // parse them to get the grammars
        // REVISIT: we'll defer this operation until there is a reference to
        //          a component from that namespace
        String sLocation = attributes.getValue(URI_XSI, XSI_SCHEMALOCATION);
        String nsLocation = attributes.getValue(URI_XSI, XSI_NONAMESPACESCHEMALOCATION);
        if (sLocation != null) {
            StringTokenizer t = new StringTokenizer(sLocation, " \n\t\r");
            String namespace, location;
            while (t.hasMoreTokens()) {
                namespace = t.nextToken ();
                if (!t.hasMoreTokens()) {
                    // REVISIT: report error for wrong number of uris
                    break;
                }
                location = t.nextToken();
                if (fGrammarResolver.getGrammar(namespace) == null)
                    fSchemaHandler.parseSchema(namespace, location);
            }
        }
        if (nsLocation != null) {
            if (fGrammarResolver.getGrammar(null) == null)
                fSchemaHandler.parseSchema(null, nsLocation);
        }

        // get the element decl for this element
        fCurrentElemDecl = null;
        fNil = false;

        XSWildcardDecl wildcard = null;
        // if there is a content model, then get the decl from that
        if (fCurrentCM != null) {
            Object decl = fCurrentCM.oneTransition(element, fCurrCMState, fSubGroupHandler);
            // it could be an element decl or a wildcard decl
            if (fCurrCMState[0] == XSCMValidator.FIRST_ERROR && fDoValidation) {
                XSComplexTypeDecl ctype = (XSComplexTypeDecl)fCurrentType;
                //REVISIT: is it the only case we will have particle = null?
                if (ctype.fParticle != null) {
                    reportSchemaError("cvc-complex-type.2.4.a", new Object[]{element.rawname, ctype.fParticle.toString()});
                } else {
                    reportSchemaError("cvc-complex-type.2.4.a", new Object[]{element.rawname, "mixed with no element content"});
                }
            }

            if (decl != null) {
                if (decl instanceof XSElementDecl) {
                    fCurrentElemDecl = (XSElementDecl)decl;
                } else {
                    wildcard = (XSWildcardDecl)decl;
                }
            }
        }

        // save the current content model state in the stack
        if (fElementDepth != -1)
            fCMStateStack[fElementDepth] = fCurrCMState;

        // increase the element depth after we've saved all states for the
        // parent element
        fElementDepth++;

        // if the wildcard is skip, then return
        if (wildcard != null && wildcard.fProcessContents == XSWildcardDecl.WILDCARD_SKIP) {
            fSkipValidationDepth = fElementDepth;
            return;
        }

        // try again to get the element decl
        if (fCurrentElemDecl == null) {
            SchemaGrammar sGrammar = fGrammarResolver.getGrammar(element.uri);
            if (sGrammar != null)
                fCurrentElemDecl = sGrammar.getGlobalElementDecl(element.localpart);
        }

        // Element Locally Valid (Element)
        // 2 Its {abstract} must be false.
        if (fCurrentElemDecl != null && fCurrentElemDecl.isAbstract())
            reportSchemaError("cvc-elt.2", new Object[]{element.rawname});

        // get the type for the current element
        fCurrentType = null;
        if (fCurrentElemDecl != null) {
            // then get the type
            fCurrentType = fCurrentElemDecl.fType;
        }

        // get type from xsi:type
        String xsiType = attributes.getValue(URI_XSI, XSI_TYPE);
        if (xsiType != null)
            getAndCheckXsiType(element, xsiType);

        // Element Locally Valid (Type)
        // 2 Its {abstract} must be false.
        if (fCurrentType != null) {
            if (fCurrentType.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
                XSComplexTypeDecl ctype = (XSComplexTypeDecl)fCurrentType;
                if (ctype.isAbstractType()) {
                    reportSchemaError("cvc-type.2", new Object[]{"Element " + element.rawname + " is declared with a type that is abstract.  Use xsi:type to specify a non-abstract type"});
                }
            }
        }

        // if the element decl is not found
        if (fCurrentType == null ) {
            if (fDoValidation) {

            // if this is the root element, or wildcard = strict, report error
            if (fElementDepth == 0) {
                // report error, because it's root element
                reportSchemaError("cvc-elt.1", new Object[]{element.rawname});
            } else if (wildcard != null &&
                wildcard.fProcessContents == XSWildcardDecl.WILDCARD_STRICT) {
                // report error, because wilcard = strict
                reportSchemaError("cvc-complex-type.2.4.c", new Object[]{element.rawname});
            }
            }
            // no element decl found, have to skip this element
            fSkipValidationDepth = fElementDepth;
            return;
        }


        // then try to get the content model
        fCurrentCM = null;
        if (fCurrentType != null) {
            if (fCurrentType.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
                fCurrentCM = ((XSComplexTypeDecl)fCurrentType).getContentModel(fCMBuilder);
            }
        }
        // and get the initial content model state
        fCurrCMState = null;
        if (fCurrentCM != null)
            fCurrCMState = fCurrentCM.startContentModel();

        // and the buffer to hold the value of the element
        fBuffer.setLength(0);
        fSawCharacters = false;

        // get information about xsi:nil
        String xsiNil = attributes.getValue(URI_XSI, XSI_NIL);
        if (xsiNil != null)
            getXsiNil(element, xsiNil);

        // now validate everything related with the attributes
        // first, get the attribute group
        XSAttributeGroupDecl attrGrp = null;
        if (fCurrentType.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
            XSComplexTypeDecl ctype = (XSComplexTypeDecl)fCurrentType;
            attrGrp = ctype.fAttrGrp;
        }
        processAttributes(element, attributes, attrGrp);

        // activate identity constraints
        if (fValidation ) {
            fValueStoreCache.startElement();
            fMatcherStack.pushContext();
            if (fCurrentElemDecl != null) {
                fValueStoreCache.initValueStoresFor(fCurrentElemDecl);
                int icCount = fCurrentElemDecl.fIDCPos;
                int uniqueOrKey = 0;
                for (;uniqueOrKey < icCount; uniqueOrKey++) {
                    if(fCurrentElemDecl.fIDConstraints[uniqueOrKey].getType() != IdentityConstraint.KEYREF ) {
                        activateSelectorFor(fCurrentElemDecl.fIDConstraints[uniqueOrKey]);
                    } else
                        break;
                }
                for (int keyref = uniqueOrKey; keyref < icCount; keyref++) {
                    activateSelectorFor((IdentityConstraint)fCurrentElemDecl.fIDConstraints[keyref]);
                }
            }

            // call all active identity constraints
            int count = fMatcherStack.getMatcherCount();
            for (int i = 0; i < count; i++) {
                XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
                matcher.startElement(element, attributes, fCurrentElemDecl);
            }
        }

    } // handleStartElement(QName,XMLAttributes,boolean)

    /** Handle end element. */
    void handleEndElement(QName element) {

        // need to pop context so that the bindings for this element is
        // discarded.
        fNamespaceSupport.popContext();

        // if we are skipping, return
        if (fSkipValidationDepth >= 0) {
            // but if this is the top element that we are skipping,
            // restore the states.
            if (fSkipValidationDepth == fElementDepth &&
                fSkipValidationDepth > 0) {
                fSkipValidationDepth = -1;
                fElementDepth--;
                fChildCount = fChildCountStack[fElementDepth];
                fCurrentElemDecl = fElemDeclStack[fElementDepth];
                fNil = fNilStack[fElementDepth];
                fCurrentType = fTypeStack[fElementDepth];
                fCurrentCM = fCMStack[fElementDepth];
                fCurrCMState = fCMStateStack[fElementDepth];
                fSawCharacters = fStringContent[fElementDepth];
            } else {
                fElementDepth--;
            }
            return;
        }

        // now validate the content of the element
        processElementContent(element);

        // Element Locally Valid (Element)
        // 6 The element information item must be �valid� with respect to each of the {identity-constraint definitions} as per Identity-constraint Satisfied (�3.11.4).

        // call matchers and de-activate context
        int oldCount = fMatcherStack.getMatcherCount();
        for (int i = oldCount - 1; i >= 0; i--) {
            XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
            matcher.endElement(element, fCurrentElemDecl); 
        }
        if (fMatcherStack.size() > 0) {
            fMatcherStack.popContext();
        }
        int newCount = fMatcherStack.getMatcherCount();
        // handle everything *but* keyref's.
        for (int i = oldCount - 1; i >= newCount; i--) {
            XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
            IdentityConstraint id;
            if((id = matcher.getIDConstraint()) != null  && id.getType() != IdentityConstraint.KEYREF) {
                matcher.endDocumentFragment();
                fValueStoreCache.transplant(id);
            } else if (id == null)
                matcher.endDocumentFragment();
        }
        // now handle keyref's/...
        for (int i = oldCount - 1; i >= newCount; i--) {
            XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
            IdentityConstraint id;
            if((id = matcher.getIDConstraint()) != null && id.getType() == IdentityConstraint.KEYREF) {
                ValueStoreBase values = fValueStoreCache.getValueStoreFor(id);
                if(values != null) // nothing to do if nothing matched!
                    values.endDocumentFragment();
                matcher.endDocumentFragment();
            }
        }
        fValueStoreCache.endElement();

        // decrease element depth and restore states
        fElementDepth--;
        if (fElementDepth == -1) {
            if (fDoValidation) {
                    // 7 If the element information item is the validation root, it must be valid per Validation Root Valid (ID/IDREF) (�3.3.4).
                    if (!fValidationState.checkIDRefID()){
                        reportSchemaError("ValidationRoot",null);
                    }
            }
        } else {
            // get the states for the parent element.
            fChildCount = fChildCountStack[fElementDepth];
            fCurrentElemDecl = fElemDeclStack[fElementDepth];
            fNil = fNilStack[fElementDepth];
            fCurrentType = fTypeStack[fElementDepth];
            fCurrentCM = fCMStack[fElementDepth];
            fCurrCMState = fCMStateStack[fElementDepth];
            fSawCharacters = fStringContent[fElementDepth];
        }

    } // handleEndElement(QName,boolean)*/


    void handleStartPrefix(String prefix, String uri) {
        // push namespace context if necessary
        if (fPushForNextBinding) {
            fNamespaceSupport.pushContext();
            fPushForNextBinding = false;
        }

        // add prefix declaration to the namespace support
        fNamespaceSupport.declarePrefix(prefix, uri.length() != 0 ? uri : null);
    }

    void getAndCheckXsiType(QName element, String xsiType) {
        // Element Locally Valid (Element)
        // 4 If there is an attribute information item among the element information item's [attributes] whose [namespace name] is identical to http://www.w3.org/2001/XMLSchema-instance and whose [local name] is type, then all of the following must be true:
        // 4.1 The �normalized value� of that attribute information item must be �valid� with respect to the built-in QName simple type, as defined by String Valid (�3.14.4);
        QName typeName = null;
        try {
            // REVISIT: have QNameDV to return QName
            //typeName = fQNameDV.validate(xsiType, fNamespaceSupport);
            fQNameDV.validate(xsiType, fValidationState);
            String prefix = fSchemaHandler.EMPTY_STRING;
            String localpart = xsiType;
            int colonptr = xsiType.indexOf(":");
            if (colonptr > 0) {
                prefix = fSymbolTable.addSymbol(xsiType.substring(0,colonptr));
                localpart = xsiType.substring(colonptr+1);
            }
            String uri = fNamespaceSupport.getURI(prefix);
            typeName = new QName(prefix, localpart, xsiType, uri);
        } catch (InvalidDatatypeValueException e) {
            reportSchemaError("cvc-elt.4.1", new Object[]{element.rawname, URI_XSI+","+XSI_TYPE, xsiType});
            return;
        }

        // 4.2 The �local name� and �namespace name� (as defined in QName Interpretation (�3.15.3)), of the �actual value� of that attribute information item must resolve to a type definition, as defined in QName resolution (Instance) (�3.15.4)
        XSTypeDecl type = null;
        SchemaGrammar grammar = fGrammarResolver.getGrammar(typeName.uri);
        if (grammar != null)
            type = grammar.getGlobalTypeDecl(typeName.localpart);
        if (type == null) {
            reportSchemaError("cvc-elt.4.2", new Object[]{element.rawname, xsiType});
            return;
        }

        // if there is no current type, set this one as current.
        // and we don't need to do extra checking
        if (fCurrentType != null) {
            // 4.3 The �local type definition� must be validly derived from the {type definition} given the union of the {disallowed substitutions} and the {type definition}'s {prohibited substitutions}, as defined in Type Derivation OK (Complex) (�3.4.6) (if it is a complex type definition), or given {disallowed substitutions} as defined in Type Derivation OK (Simple) (�3.14.6) (if it is a simple type definition).
            int block = fCurrentElemDecl.fBlock;
            if (fCurrentType.getXSType() == XSTypeDecl.COMPLEX_TYPE)
                block |= ((XSComplexTypeDecl)fCurrentType).fBlock;
            if (!XSConstraints.checkTypeDerivationOk(type, fCurrentType, block))
                reportSchemaError("cvc-elt.4.3", new Object[]{element.rawname, xsiType});
        }

        fCurrentType = type;
    }

    void getXsiNil(QName element, String xsiNil) {
        // Element Locally Valid (Element)
        // 3 The appropriate case among the following must be true:
        // 3.1 If {nillable} is false, then there must be no attribute information item among the element information item's [attributes] whose [namespace name] is identical to http://www.w3.org/2001/XMLSchema-instance and whose [local name] is nil.
        if (fCurrentElemDecl != null && !fCurrentElemDecl.isNillable())
            reportSchemaError("cvc-elt.3.1", new Object[]{element.rawname, URI_XSI+","+XSI_NIL});

        // 3.2 If {nillable} is true and there is such an attribute information item and its �actual value� is true , then all of the following must be true:
        // 3.2.2 There must be no fixed {value constraint}.
        if (xsiNil.equals(SchemaSymbols.ATTVAL_TRUE) ||
            xsiNil.equals(SchemaSymbols.ATTVAL_TRUE_1)) {
            fNil = true;
            if (fCurrentElemDecl != null &&
                fCurrentElemDecl.getConstraintType() == XSElementDecl.FIXED_VALUE) {
                reportSchemaError("cvc-elt.3.2.2", new Object[]{element.rawname, URI_XSI+","+XSI_NIL});
            }
        }
    }

    void processAttributes(QName element, XMLAttributes attributes, XSAttributeGroupDecl attrGrp) {

        // add default attributes
        if (attrGrp != null) {
            addDefaultAttributes(element, attributes, attrGrp);
        }

        // if we don't do validation, we don't need to validate the attributes
        if (!fDoValidation)
            return;

        // Element Locally Valid (Type)
        // 3.1.1 The element information item's [attributes] must be empty, excepting those whose [namespace name] is identical to http://www.w3.org/2001/XMLSchema-instance and whose [local name] is one of type, nil, schemaLocation or noNamespaceSchemaLocation.
        if (fCurrentType == null || fCurrentType.getXSType() == XSTypeDecl.SIMPLE_TYPE) {
            int attCount = attributes.getLength();
            for (int index = 0; index < attCount; index++) {
                attributes.getName(index, fTempQName);
                if (fTempQName.uri == URI_XSI) {
                    if (fTempQName.localpart != XSI_SCHEMALOCATION &&
                        fTempQName.localpart != XSI_NONAMESPACESCHEMALOCATION &&
                        fTempQName.localpart != XSI_NIL &&
                        fTempQName.localpart != XSI_TYPE) {
                        reportSchemaError("cvc-type.3.1.1", new Object[]{element.rawname});
                    }
                } else if (fTempQName.rawname != XMLNS && !fTempQName.rawname.startsWith("xmlns:")) {
                    reportSchemaError("cvc-type.3.1.1", new Object[]{element.rawname});
                }
            }
            return;
        }

        XSAttributeUse attrUses[] = attrGrp.getAttributeUses();
        int useCount = attrUses.length;
        XSWildcardDecl attrWildcard = attrGrp.fAttributeWC;

        // whether we have seen a Wildcard ID.
        String wildcardIDName = null;

        // for each present attribute
        int attCount = attributes.getLength();

        // Element Locally Valid (Complex Type)
        // 3 For each attribute information item in the element information item's [attributes] excepting those whose [namespace name] is identical to http://www.w3.org/2001/XMLSchema-instance and whose [local name] is one of type, nil, schemaLocation or noNamespaceSchemaLocation, the appropriate case among the following must be true:
        // get the corresponding attribute decl
        for (int index = 0; index < attCount; index++) {
            attributes.getName(index, fTempQName);
            // if it's from xsi namespace, it must be one of the four
            if (fTempQName.uri == URI_XSI) {
                if (fTempQName.localpart == XSI_SCHEMALOCATION ||
                    fTempQName.localpart == XSI_NONAMESPACESCHEMALOCATION ||
                    fTempQName.localpart == XSI_NIL ||
                    fTempQName.localpart == XSI_TYPE) {
                    continue;
                }
            } else if (fTempQName.rawname == XMLNS || fTempQName.rawname.startsWith("xmlns:")) {
                continue;
            }
            // it's not xmlns, and not xsi, then we need to find a decl for it
            XSAttributeUse currUse = null;
            for (int i = 0; i < useCount; i++) {
                if (attrUses[i].fAttrDecl.fName == fTempQName.localpart &&
                    attrUses[i].fAttrDecl.fTargetNamespace == fTempQName.uri) {
                    currUse = attrUses[i];
                    break;
                }
            }

            // 3.2 otherwise all of the following must be true:
            // 3.2.1 There must be an {attribute wildcard}.
            // 3.2.2 The attribute information item must be �valid� with respect to it as defined in Item Valid (Wildcard) (�3.10.4).

            // if failed, get it from wildcard
            if (currUse == null) {
                //if (attrWildcard == null)
                //    reportSchemaError("cvc-complex-type.3.2.1", new Object[]{element.rawname, fTempQName.rawname});
                if (attrWildcard == null ||
                    !attrWildcard.allowNamespace(fTempQName.uri)) {
                    // so this attribute is not allowed
                    reportSchemaError("cvc-complex-type.3.2.2", new Object[]{element.rawname, fTempQName.rawname});
                    continue;
                }
            }

            XSAttributeDecl currDecl = null;
            if (currUse != null) {
                currDecl = currUse.fAttrDecl;
            } else {
                // which means it matches a wildcard
                // skip it if processContents is skip
                if (attrWildcard.fProcessContents == XSWildcardDecl.WILDCARD_SKIP)
                    continue;
                // now get the grammar and attribute decl
                SchemaGrammar grammar = fGrammarResolver.getGrammar(fTempQName.uri);
                if (grammar != null)
                    currDecl = grammar.getGlobalAttributeDecl(fTempQName.localpart);
                // if can't find
                if (currDecl == null) {
                    // if strict, report error
                    if (attrWildcard.fProcessContents == XSWildcardDecl.WILDCARD_STRICT)
                        reportSchemaError("cvc-complex-type.3.2.2", new Object[]{element.rawname, fTempQName.rawname});
                    // then continue to the next attribute
                    continue;
                } else {
                    // 5 Let [Definition:]  the wild IDs be the set of all attribute information item to which clause 3.2 applied and whose �validation� resulted in a �context-determined declaration� of mustFind or no �context-determined declaration� at all, and whose [local name] and [namespace name] resolve (as defined by QName resolution (Instance) (�3.15.4)) to an attribute declaration whose {type definition} is or is derived from ID. Then all of the following must be true:
                    // 5.1 There must be no more than one item in �wild IDs�.
                    if (currDecl.fType instanceof IDDatatypeValidator) {
                        if (wildcardIDName != null)
                            reportSchemaError("cvc-complex-type.5.1", new Object[]{element.rawname, currDecl.fName, wildcardIDName});
                        else
                            wildcardIDName = currDecl.fName;
                    }
                }
            }

            // Attribute Locally Valid
            // For an attribute information item to be locally �valid� with respect to an attribute declaration all of the following must be true:
            // 1 The declaration must not be �absent� (see Missing Sub-components (�5.3) for how this can fail to be the case).
            // 2 Its {type definition} must not be absent.
            // 3 The item's �normalized value� must be locally �valid� with respect to that {type definition} as per String Valid (�3.14.4).
            // get simple type
            DatatypeValidator attDV = currDecl.fType;
            // get attribute value
            String attrValue = attributes.getValue(index);
            // normalize it
            // REVISIT: or should the normalize() be called within validate()?
            attrValue = XSAttributeChecker.normalize(attrValue, attDV.getWSFacet());
            Object actualValue = null;
            try {
                attDV.validate(attrValue, fValidationState);
            } catch (InvalidDatatypeValueException idve) {
                reportSchemaError("cvc-attribute.3", new Object[]{element.rawname, fTempQName.rawname, attrValue});
            }

            // get the value constraint from use or decl
            // 4 The item's �actual value� must match the value of the {value constraint}, if it is present and fixed.                 // now check the value against the simpleType
            if (currDecl.fConstraintType == XSAttributeDecl.FIXED_VALUE) {
                // REVISIT: compare should be equal, and takes object, instead of string
                //          do it in the new datatype design
                //if (attDV.compare((String)actualValue, (String)currDecl.fDefault) != 0)
                if (attDV.compare(attrValue, (String)currDecl.fDefault) != 0)
                    reportSchemaError("cvc-attribute.4", new Object[]{element.rawname, fTempQName.rawname, attrValue});
            }

            // 3.1 If there is among the {attribute uses} an attribute use with an {attribute declaration} whose {name} matches the attribute information item's [local name] and whose {target namespace} is identical to the attribute information item's [namespace name] (where an �absent� {target namespace} is taken to be identical to a [namespace name] with no value), then the attribute information must be �valid� with respect to that attribute use as per Attribute Locally Valid (Use) (�3.5.4). In this case the {attribute declaration} of that attribute use is the �context-determined declaration� for the attribute information item with respect to Schema-Validity Assessment (Attribute) (�3.2.4) and Assessment Outcome (Attribute) (�3.2.5).
            if (currUse != null && currUse.fConstraintType == XSAttributeDecl.FIXED_VALUE) {
                // REVISIT: compare should be equal, and takes object, instead of string
                //          do it in the new datatype design
                //if (attDV.compare((String)actualValue, (String)currUse.fDefault) != 0)
                if (attDV.compare(attrValue, (String)currUse.fDefault) != 0)
                    reportSchemaError("cvc-complex-type.3.1", new Object[]{element.rawname, fTempQName.rawname, attrValue});
            }
        } // end of for (all attributes)

        // 5.2 If �wild IDs� is non-empty, there must not be any attribute uses among the {attribute uses} whose {attribute declaration}'s {type definition} is or is derived from ID.
        if (attrGrp.fIDAttrName != null && wildcardIDName != null)
            reportSchemaError("cvc-complex-type.5.2", new Object[]{element.rawname, wildcardIDName, attrGrp.fIDAttrName});

    } //processAttributes

    void addDefaultAttributes(QName element, XMLAttributes attributes, XSAttributeGroupDecl attrGrp) {
        // Check after all specified attrs are scanned
        // (1) report error for REQUIRED attrs that are missing (V_TAGc)
        // REVISIT: should we check prohibited attributes?
        // (2) report error for PROHIBITED attrs that are present (V_TAGc)
        // (3) add default attrs (FIXED and NOT_FIXED)
        //
        if (DEBUG) {
            System.out.println("addDefaultAttributes: " + element);
        }
        XSAttributeUse attrUses[] = attrGrp.getAttributeUses();
        int useCount = attrUses.length;
        XSAttributeUse currUse;
        XSAttributeDecl currDecl;
        short constType;
        Object defaultValue;
        boolean isSpecified;
        QName attName;
        // for each attribute use
        for (int i = 0; i < useCount; i++) {
            currUse = attrUses[i];
            currDecl = currUse.fAttrDecl;
            // get value constraint
            constType = currUse.fConstraintType;
            defaultValue = currUse.fDefault;
            if (constType == XSAttributeDecl.NO_CONSTRAINT) {
                constType = currDecl.fConstraintType;
                defaultValue = currDecl.fDefault;
            }
            // whether this attribute is specified
            isSpecified = attributes.getValue(currDecl.fTargetNamespace, currDecl.fName) != null;

            // Element Locally Valid (Complex Type)
            // 4 The {attribute declaration} of each attribute use in the {attribute uses} whose {required} is true matches one of the attribute information items in the element information item's [attributes] as per clause 3.1 above.
            if (currUse.fUse == SchemaSymbols.USE_REQUIRED) {
                if (!isSpecified)
                    reportSchemaError("cvc-complex-type.4", new Object[]{element.rawname, currDecl.fName});
            }
            // if the attribute is not specified, then apply the value constraint
            if (!isSpecified && constType != XSAttributeDecl.NO_CONSTRAINT) {
                attName = new QName(null, currDecl.fName, currDecl.fName, currDecl.fTargetNamespace);
                //REVISIT: what's the proper attrType?
                attributes.addAttribute(attName, null, (defaultValue !=null)?defaultValue.toString():"");
            }


        } // for
    } // addDefaultAttributes

    void processElementContent(QName element) {
        // fCurrentElemDecl: default value; ...
        if(fCurrentElemDecl != null) {
            if(fCurrentElemDecl.fDefault != null) {
                if(fBuffer.toString().trim().length() == 0) {
                    int bufLen = fCurrentElemDecl.fDefault.toString().length();
                    char [] chars = new char[bufLen];
                    fCurrentElemDecl.fDefault.toString().getChars(0, bufLen, chars, 0);
                    XMLString text = new XMLString(chars, 0, bufLen);
                    if(fDocumentHandler != null) {
                        fDocumentHandler.characters(text);
                    }
                    // call all active identity constraints
                    int count = fMatcherStack.getMatcherCount();
                    for (int i = 0; i < count; i++) {
                        XPathMatcher matcher = fMatcherStack.getMatcherAt(i);
                        matcher.characters(text);
                    }
                }
            }
        }
        // fixed values are handled later, after xsi:type determined.

        if (DEBUG) {
          System.out.println("processElementContent:" +element);
        }

        if (fCurrentElemDecl != null &&
            fCurrentElemDecl.getConstraintType() == XSElementDecl.DEFAULT_VALUE) {
        }

        if (fDoValidation) {
            String content = fBuffer.toString();

            // Element Locally Valid (Element)
            // 3.2.1 The element information item must have no character or element information item [children].
            if (fNil) {
                if (fChildCount != 0 || content.length() != 0)
                    reportSchemaError("cvc-elt.3.2.1", new Object[]{element.rawname, URI_XSI+","+XSI_NIL});
            }

            // 5 The appropriate case among the following must be true:
            // 5.1 If the declaration has a {value constraint}, the item has neither element nor character [children] and clause 3.2 has not applied, then all of the following must be true:
            if (fCurrentElemDecl != null &&
                fCurrentElemDecl.getConstraintType() != XSElementDecl.NO_CONSTRAINT &&
                fChildCount == 0 && content.length() == 0 && !fNil) {
                // 5.1.1 If the �actual type definition� is a �local type definition� then the canonical lexical representation of the {value constraint} value must be a valid default for the �actual type definition� as defined in Element Default Valid (Immediate) (�3.3.6).
                if (fCurrentType != fCurrentElemDecl.fType) {
                    if (!XSConstraints.ElementDefaultValidImmediate(fCurrentType, fCurrentElemDecl.fDefault.toString()))
                        reportSchemaError("cvc-elt.5.1.1", new Object[]{element.rawname, fCurrentType.getXSTypeName(), fCurrentElemDecl.fDefault.toString()});
                }
                // 5.1.2 The element information item with the canonical lexical representation of the {value constraint} value used as its �normalized value� must be �valid� with respect to the �actual type definition� as defined by Element Locally Valid (Type) (�3.3.4).
                elementLocallyValidType(element, fCurrentElemDecl.fDefault.toString());
            } else {
                // 5.2 If the declaration has no {value constraint} or the item has either element or character [children] or clause 3.2 has applied, then all of the following must be true:
                // 5.2.1 The element information item must be �valid� with respect to the �actual type definition� as defined by Element Locally Valid (Type) (�3.3.4).
                Object actualValue = elementLocallyValidType(element, content);
                // 5.2.2 If there is a fixed {value constraint} and clause 3.2 has not applied, all of the following must be true:
                if (fCurrentElemDecl != null &&
                    fCurrentElemDecl.getConstraintType() == XSElementDecl.FIXED_VALUE &&
                    !fNil) {
                    // 5.2.2.1 The element information item must have no element information item [children].
                    if (fChildCount != 0)
                        reportSchemaError("cvc-elt.5.2.2.1", new Object[]{element.rawname});
                    // 5.2.2.2 The appropriate case among the following must be true:
                    if (fCurrentType.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
                        XSComplexTypeDecl ctype = (XSComplexTypeDecl)fCurrentType;
                        // 5.2.2.2.1 If the {content type} of the �actual type definition� is mixed, then the �initial value� of the item must match the canonical lexical representation of the {value constraint} value.
                        if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_MIXED) {
                            // REVISIT: how to get the initial value, does whiteSpace count?
                            if (!fCurrentElemDecl.fDefault.toString().equals(content))
                                reportSchemaError("cvc-elt.5.2.2.2.1", new Object[]{element.rawname, content, fCurrentElemDecl.fDefault.toString()});
                        }
                        // 5.2.2.2.2 If the {content type} of the �actual type definition� is a simple type definition, then the �actual value� of the item must match the canonical lexical representation of the {value constraint} value.
                        else if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_SIMPLE) {
                            // REVISIT: compare should be equal, and takes object, instead of string
                            //          do it in the new datatype design
                            if (ctype.fDatatypeValidator.compare((String)actualValue, (String)fCurrentElemDecl.fDefault) != 0)
                                reportSchemaError("cvc-elt.5.2.2.2.2", new Object[]{element.rawname, content, fCurrentElemDecl.fDefault.toString()});
                        }
                    } else if (fCurrentType.getXSType() == XSTypeDecl.SIMPLE_TYPE) {
                        DatatypeValidator sType = (DatatypeValidator)fCurrentType;
                        // REVISIT: compare should be equal, and takes object, instead of string
                        //          do it in the new datatype design
                        if (sType.compare((String)actualValue, (String)fCurrentElemDecl.fDefault) != 0)
                            reportSchemaError("cvc-elt.5.2.2.2.2", new Object[]{element.rawname, content, fCurrentElemDecl.fDefault.toString()});
                    }
                }
            }
        } // if fDoValidation
    } // processElementContent

    Object elementLocallyValidType(QName element, String textContent) {
        if (fCurrentType == null)
            return null;

        Object retValue = null;
        // Element Locally Valid (Type)
        // 3 The appropriate case among the following must be true:
        // 3.1 If the type definition is a simple type definition, then all of the following must be true:
        if (fCurrentType.getXSType() == XSTypeDecl.SIMPLE_TYPE) {
            // 3.1.2 The element information item must have no element information item [children].
            if (fChildCount != 0)
                reportSchemaError("cvc-type.3.1.2", new Object[]{element.rawname});
            // 3.1.3 If clause 3.2 of Element Locally Valid (Element) (�3.3.4) did not apply, then the �normalized value� must be �valid� with respect to the type definition as defined by String Valid (�3.14.4).
            if (!fNil) {
                DatatypeValidator dv = (DatatypeValidator)fCurrentType;
                // REVISIT: or should the normalize() be called within validate()?
                String content = XSAttributeChecker.normalize(textContent, dv.getWSFacet());
                try {
                    // REVISIT: use XSSimpleTypeDecl.ValidateContext to replace null
                    // retValue = dv.validate(content, null);
                    // make sure we return something... - NG
                    retValue = content;
                    dv.validate(content, fValidationState);
                } catch (InvalidDatatypeValueException e) {
                    reportSchemaError("cvc-type.3.1.3", new Object[]{element.rawname, content});
                }
            }
        } else {
            // 3.2 If the type definition is a complex type definition, then the element information item must be �valid� with respect to the type definition as per Element Locally Valid (Complex Type) (�3.4.4);
            retValue = elementLocallyValidComplexType(element, textContent);
        }

        return retValue;
    } // elementLocallyValidType

    Object elementLocallyValidComplexType(QName element, String textContent) {
        Object actualValue = null;
        XSComplexTypeDecl ctype = (XSComplexTypeDecl)fCurrentType;

        // Element Locally Valid (Complex Type)
        // For an element information item to be locally �valid� with respect to a complex type definition all of the following must be true:
        // 1 {abstract} is false.
        // 2 If clause 3.2 of Element Locally Valid (Element) (�3.3.4) did not apply, then the appropriate case among the following must be true:
        if (!fNil) {
            // 2.1 If the {content type} is empty, then the element information item has no character or element information item [children].
            if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_EMPTY &&
                (fChildCount != 0 || textContent.length() != 0)) {
                reportSchemaError("cvc-complex-type.2.1", new Object[]{element.rawname});
            }
            // 2.2 If the {content type} is a simple type definition, then the element information item has no element information item [children], and the �normalized value� of the element information item is �valid� with respect to that simple type definition as defined by String Valid (�3.14.4).
            else if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_SIMPLE) {
                if (fChildCount != 0)
                    reportSchemaError("cvc-complex-type.2.2", new Object[]{element.rawname});
                DatatypeValidator dv = ctype.fDatatypeValidator;
                // REVISIT: or should the normalize() be called within validate()?
                String content = XSAttributeChecker.normalize(textContent, dv.getWSFacet());
                try {
                    // REVISIT: use XSSimpleTypeDecl.ValidateContext to replace null
                    dv.validate(content, fValidationState);
                } catch (InvalidDatatypeValueException e) {
                    reportSchemaError("cvc-complex-type.2.2", new Object[]{element.rawname});
                }
                // eventually, this method should return the same actualValue as elementLocallyValidType...
                // obviously it'll return null when the content is complex.
                actualValue = content;
            }
            // 2.3 If the {content type} is element-only, then the element information item has no character information item [children] other than those whose [character code] is defined as a white space in [XML 1.0 (Second Edition)].
            else if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_ELEMENT) {
                // REVISIT: how to check whether there is any text content?
                if (fSawCharacters) {
                    reportSchemaError("cvc-complex-type.2.3", new Object[]{element.rawname});
                }
            }
            // 2.4 If the {content type} is element-only or mixed, then the sequence of the element information item's element information item [children], if any, taken in order, is �valid� with respect to the {content type}'s particle, as defined in Element Sequence Locally Valid (Particle) (�3.9.4).
            if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_ELEMENT ||
                ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_MIXED) {
                // if the current state is a valid state, check whether
                // it's one of the final states.
                if (DEBUG) {
                  System.out.println(fCurrCMState);
                }
                if (fCurrCMState[0] >= 0 &&
                    !fCurrentCM.endContentModel(fCurrCMState)) {
                    reportSchemaError("cvc-complex-type.2.4.b", new Object[]{element.rawname, ctype.fParticle.toString()});
                }
            }
        }
        return actualValue;
    } // elementLocallyValidComplexType

    void reportSchemaError(String key, Object[] arguments) {
        if (fDoValidation)
            fErrorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                       key, arguments,
                                       XMLErrorReporter.SEVERITY_ERROR);
    }

    /**********************************/

    // xpath matcher information

    /**
     * Stack of XPath matchers for identity constraints.
     *
     * @author Andy Clark, IBM
     */
    protected static class XPathMatcherStack {

        //
        // Data
        //

        /** Active matchers. */
        protected XPathMatcher[] fMatchers = new XPathMatcher[4];

        /** Count of active matchers. */
        protected int fMatchersCount;

        /** Offset stack for contexts. */
        protected IntStack fContextStack = new IntStack();

        //
        // Constructors
        //

        public XPathMatcherStack() {
        } // <init>()

        //
        // Public methods
        //

        /** Resets the XPath matcher stack. */
        public void clear() {
            for (int i = 0; i < fMatchersCount; i++) {
                fMatchers[i] = null;
            }
            fMatchersCount = 0;
            fContextStack.clear();
        } // clear()

        /** Returns the size of the stack. */
        public int size() {
            return fContextStack.size();
        } // size():int

        /** Returns the count of XPath matchers. */
        public int getMatcherCount() {
            return fMatchersCount;
        } // getMatcherCount():int

        /** Adds a matcher. */
        public void addMatcher(XPathMatcher matcher) {
            ensureMatcherCapacity();
            fMatchers[fMatchersCount++] = matcher;
        } // addMatcher(XPathMatcher)

        /** Returns the XPath matcher at the specified index. */
        public XPathMatcher getMatcherAt(int index) {
            return fMatchers[index];
        } // getMatcherAt(index):XPathMatcher

        /** Pushes a new context onto the stack. */
        public void pushContext() {
            fContextStack.push(fMatchersCount);
        } // pushContext()

        /** Pops a context off of the stack. */
        public void popContext() {
            fMatchersCount = fContextStack.pop();
        } // popContext()

        //
        // Private methods
        //

        /** Ensures the size of the matchers array. */
        private void ensureMatcherCapacity() {
            if (fMatchersCount == fMatchers.length) {
                XPathMatcher[] array = new XPathMatcher[fMatchers.length * 2];
                System.arraycopy(fMatchers, 0, array, 0, fMatchers.length);
                fMatchers = array;
            }
        } // ensureMatcherCapacity()

    } // class XPathMatcherStack

    // value store implementations

    /**
     * Value store implementation base class. There are specific subclasses
     * for handling unique, key, and keyref.
     *
     * @author Andy Clark, IBM
     */
    protected abstract class ValueStoreBase
        implements ValueStore {

        //
        // Constants
        //

        /** Not a value (Unicode: #FFFF). */
        protected IDValue NOT_AN_IDVALUE = new IDValue("\uFFFF", null);

        //
        // Data
        //

        /** Identity constraint. */
        protected IdentityConstraint fIdentityConstraint;

        /** Current data values. */
        protected final OrderedHashtable fValues = new OrderedHashtable();

        /** Current data value count. */
        protected int fValuesCount;

        /** Data value tuples. */
        protected final Vector fValueTuples = new Vector();

        //
        // Constructors
        //

        /** Constructs a value store for the specified identity constraint. */
        protected ValueStoreBase(IdentityConstraint identityConstraint) {
            fIdentityConstraint = identityConstraint;
        } // <init>(IdentityConstraint)

        //
        // Public methods
        //

        // destroys this ValueStore; useful when, for instance, a
        // locally-scoped ID constraint is involved.
        public void destroy() {
            fValuesCount = 0;
            fValues.clear();
            fValueTuples.removeAllElements();
        } // end destroy():void

        // appends the contents of one ValueStore to those of us.
        public void append(ValueStoreBase newVal) {
            for (int i = 0; i < newVal.fValueTuples.size(); i++) {
                OrderedHashtable o = (OrderedHashtable)newVal.fValueTuples.elementAt(i);
                if (!contains(o))
                    fValueTuples.addElement(o);
            }
        } // append(ValueStoreBase)

        /** Start scope for value store. */
        public void startValueScope() throws XNIException {
            fValuesCount = 0;
            int count = fIdentityConstraint.getFieldCount();
            for (int i = 0; i < count; i++) {
                fValues.put(fIdentityConstraint.getFieldAt(i), NOT_AN_IDVALUE);
            }
        } // startValueScope()

        /** Ends scope for value store. */
        public void endValueScope() throws XNIException {

            // is there anything to do?
            // REVISIT: This check solves the problem with field matchers
            //          that get activated because they are at the same
            //          level as the declaring element (e.g. selector xpath
            //          is ".") but never match.
            //          However, this doesn't help us catch the problem
            //          when we expect a field value but never see it. A
            //          better solution has to be found. -Ac
            // REVISIT: Is this a problem? -Ac
            // Yes - NG
            if (fValuesCount == 0) {
                if(fIdentityConstraint.getType() == IdentityConstraint.KEY) {
                    String code = "AbsentKeyValue";
                    String eName = fIdentityConstraint.getElementName();
                    reportSchemaError(code, new Object[]{eName});
                }
                return;
            }

            // do we have enough values?
            if (fValuesCount != fIdentityConstraint.getFieldCount()) {
                switch (fIdentityConstraint.getType()) {
                    case IdentityConstraint.UNIQUE: {
                        String code = "UniqueNotEnoughValues";
                        String ename = fIdentityConstraint.getElementName();
                        reportSchemaError(code, new Object[]{ename});
                        break;
                    }
                    case IdentityConstraint.KEY: {
                        String code = "KeyNotEnoughValues";
                        UniqueOrKey key = (UniqueOrKey)fIdentityConstraint;
                        String ename = fIdentityConstraint.getElementName();
                        String kname = key.getIdentityConstraintName();
                        reportSchemaError(code, new Object[]{ename,kname});
                        break;
                    }
                    case IdentityConstraint.KEYREF: {
                        String code = "KeyRefNotEnoughValues";
                        KeyRef keyref = (KeyRef)fIdentityConstraint;
                        String ename = fIdentityConstraint.getElementName();
                        String kname = (keyref.getKey()).getIdentityConstraintName();
                        reportSchemaError(code, new Object[]{ename,kname});
                        break;
                    }
                }
                return;
            }


        } // endValueScope()

        // This is needed to allow keyref's to look for matched keys
        // in the correct scope.  Unique and Key may also need to
        // override this method for purposes of their own.
        // This method is called whenever the DocumentFragment
        // of an ID Constraint goes out of scope.
        public void endDocumentFragment() throws XNIException {
        } // endDocumentFragment():void

        /**
         * Signals the end of the document. This is where the specific
         * instances of value stores can verify the integrity of the
         * identity constraints.
         */
        public void endDocument() throws XNIException {
        } // endDocument()

        //
        // ValueStore methods
        //

        /* reports an error if an element is matched
         * has nillable true and is matched by a key.
         */

        public void reportNilError(IdentityConstraint id) {
            if(id.getType() == IdentityConstraint.KEY) {
                String code = "KeyMatchesNillable";
                reportSchemaError(code, new Object[]{id.getElementName()});
            }
        } // reportNilError

        /**
         * Adds the specified value to the value store.
         *
         * @param value The value to add.
         * @param field The field associated to the value. This reference
         *              is used to ensure that each field only adds a value
         *              once within a selection scope.
         */
        public void addValue(Field field, IDValue value) {
            if(!field.mayMatch()) {
                String code = "FieldMultipleMatch";
                reportSchemaError(code, new Object[]{field.toString()});
            }

            // do we even know this field?
            int index = fValues.indexOf(field);
            if (index == -1) {
                String code = "UnknownField";
                reportSchemaError(code, new Object[]{field.toString()});
                return;
            }

            // store value
            IDValue storedValue = fValues.valueAt(index);
            if (storedValue.isDuplicateOf(NOT_AN_IDVALUE)) {
                fValuesCount++;
            }
            fValues.put(field, value);

            if (fValuesCount == fValues.size()) {
                // is this value as a group duplicated?
                if (contains(fValues)) {
                    duplicateValue(fValues);
                }

                // store values
                OrderedHashtable values = (OrderedHashtable)fValues.clone();
                fValueTuples.addElement(values);
            }

        } // addValue(String,Field)

        /**
         * Returns true if this value store contains the specified
         * values tuple.
         */
        public boolean contains(OrderedHashtable tuple) {

            // do sizes match?
            int tcount = tuple.size();

            // iterate over tuples to find it
            int count = fValueTuples.size();
            LOOP: for (int i = 0; i < count; i++) {
                OrderedHashtable vtuple = (OrderedHashtable)fValueTuples.elementAt(i);
                // compare values
                for (int j = 0; j < tcount; j++) {
                    IDValue value1 = vtuple.valueAt(j);
                    IDValue value2 = tuple.valueAt(j);
                    if(!(value1.isDuplicateOf(value2))) {
                        continue LOOP;
                    }
                }

                // found it
                return true;
            }

            // didn't find it
            return false;

        } // contains(Hashtable):boolean

        //
        // Protected methods
        //

        /**
         * Called when a duplicate value is added. Subclasses should override
         * this method to perform error checking.
         *
         * @param tuple The duplicate value tuple.
         */
        protected void duplicateValue(OrderedHashtable tuple)
            throws XNIException {
            // no-op
        } // duplicateValue(Hashtable)

        /** Returns a string of the specified values. */
        protected String toString(OrderedHashtable tuple) {

            // no values
            int size = tuple.size();
            if (size == 0) {
                return "";
            }

            // construct value string
            StringBuffer str = new StringBuffer();
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    str.append(',');
                }
                str.append(tuple.valueAt(i));
            }
            return str.toString();

        } // toString(OrderedHashtable):String

        //
        // Object methods
        //

        /** Returns a string representation of this object. */
        public String toString() {
            String s = super.toString();
            int index1 = s.lastIndexOf('$');
            if (index1 != -1) {
                s = s.substring(index1 + 1);
            }
            int index2 = s.lastIndexOf('.');
            if (index2 != -1) {
                s = s.substring(index2 + 1);
            }
            return s + '[' + fIdentityConstraint + ']';
        } // toString():String

    } // class ValueStoreBase

    /**
     * Unique value store.
     *
     * @author Andy Clark, IBM
     */
    protected class UniqueValueStore
        extends ValueStoreBase {

        //
        // Constructors
        //

        /** Constructs a unique value store. */
        public UniqueValueStore(UniqueOrKey unique) {
            super(unique);
        } // <init>(Unique)

        //
        // ValueStoreBase protected methods
        //

        /**
         * Called when a duplicate value is added.
         *
         * @param tuple The duplicate value tuple.
         */
        protected void duplicateValue(OrderedHashtable tuple)
            throws XNIException {
            String code = "DuplicateUnique";
            String value = toString(tuple);
            String ename = fIdentityConstraint.getElementName();
            reportSchemaError(code, new Object[]{value,ename});
        } // duplicateValue(Hashtable)

    } // class UniqueValueStore

    /**
     * Key value store.
     *
     * @author Andy Clark, IBM
     */
    protected class KeyValueStore
        extends ValueStoreBase {

        // REVISIT: Implement a more efficient storage mechanism. -Ac

        //
        // Constructors
        //

        /** Constructs a key value store. */
        public KeyValueStore(UniqueOrKey key) {
            super(key);
        } // <init>(Key)

        //
        // ValueStoreBase protected methods
        //

        /**
         * Called when a duplicate value is added.
         *
         * @param tuple The duplicate value tuple.
         */
        protected void duplicateValue(OrderedHashtable tuple)
            throws XNIException {
            String code = "DuplicateKey";
            String value = toString(tuple);
            String ename = fIdentityConstraint.getElementName();
            reportSchemaError(code, new Object[]{value,ename});
        } // duplicateValue(Hashtable)

    } // class KeyValueStore

    /**
     * Key reference value store.
     *
     * @author Andy Clark, IBM
     */
    protected class KeyRefValueStore
        extends ValueStoreBase {

        //
        // Data
        //

        /** Key value store. */
        protected ValueStoreBase fKeyValueStore;

        //
        // Constructors
        //

        /** Constructs a key value store. */
        public KeyRefValueStore(KeyRef keyRef, KeyValueStore keyValueStore) {
            super(keyRef);
            fKeyValueStore = keyValueStore;
        } // <init>(KeyRef)

        //
        // ValueStoreBase methods
        //

        // end the value Scope; here's where we have to tie
        // up keyRef loose ends.
        public void endDocumentFragment () throws XNIException {

            // do all the necessary management...
            super.endDocumentFragment ();

            // verify references
            // get the key store corresponding (if it exists):
            fKeyValueStore = (ValueStoreBase)fValueStoreCache.fGlobalIDConstraintMap.get(((KeyRef)fIdentityConstraint).getKey());

            if(fKeyValueStore == null) {
                // report error
                String code = "KeyRefOutOfScope";
                String value = fIdentityConstraint.toString();
                reportSchemaError(code, new Object[]{value});
                return;
            }

            int count = fValueTuples.size();
            for (int i = 0; i < count; i++) {
                OrderedHashtable values = (OrderedHashtable)fValueTuples.elementAt(i);
                if (!fKeyValueStore.contains(values)) {
                    String code = "KeyNotFound";
                    String value = toString(values);
                    String element = fIdentityConstraint.getElementName();
                    reportSchemaError(code, new Object[]{value,element});
                }
            }

        } // endDocumentFragment()

        /** End document. */
        public void endDocument() throws XNIException {
            super.endDocument();

        } // endDocument()

    } // class KeyRefValueStore

    // value store management

    /**
     * Value store cache. This class is used to store the values for
     * identity constraints.
     *
     * @author Andy Clark, IBM
     */
    protected class ValueStoreCache {

        //
        // Data
        //

        // values stores

        /** stores all global Values stores. */
        protected final Vector fValueStores = new Vector();

        /** Values stores associated to specific identity constraints. */
        protected final Hashtable fIdentityConstraint2ValueStoreMap = new Hashtable();

        // sketch of algorithm:
        // - when a constraint is first encountered, its
        //  values are stored in the (local) fIdentityConstraint2ValueStoreMap;
        // - Once it is validated (i.e., wen it goes out of scope),
        //  its values are merged into the fGlobalIDConstraintMap;
        // - as we encounter keyref's, we look at the global table to
        //  validate them.
        // the fGlobalIDMapStack has the following structure:
        // - validation always occurs against the fGlobalIDConstraintMap
        // (which comprises all the "eligible" id constraints);
        // When an endelement is found, this Hashtable is merged with the one
        // below in the stack.
        // When a start tag is encountered, we create a new
        // fGlobalIDConstraintMap.
        // i.e., the top of the fGlobalIDMapStack always contains
        // the preceding siblings' eligible id constraints;
        // the fGlobalIDConstraintMap contains descendants+self.
        // keyrefs can only match descendants+self.
        protected final Stack fGlobalMapStack = new Stack();
        protected final Hashtable fGlobalIDConstraintMap = new Hashtable();

        //
        // Constructors
        //

        /** Default constructor. */
        public ValueStoreCache() {
        } // <init>()

        //
        // Public methods
        //

        /** Resets the identity constraint cache. */
        public void startDocument() throws XNIException {
            fValueStores.removeAllElements();
            fIdentityConstraint2ValueStoreMap.clear();
            fGlobalIDConstraintMap.clear();
            fGlobalMapStack.removeAllElements();
        } // startDocument()

        // startElement:  pushes the current fGlobalIDConstraintMap
        // onto fGlobalMapStack and clears fGlobalIDConstraint map.
        public void startElement() {
            fGlobalMapStack.push(fGlobalIDConstraintMap.clone());
            fGlobalIDConstraintMap.clear();
        } // startElement(void)

        // endElement():  merges contents of fGlobalIDConstraintMap with the
        // top of fGlobalMapStack into fGlobalIDConstraintMap.
        public void endElement() {
            if (fGlobalMapStack.isEmpty()) return; // must be an invalid doc!
            Hashtable oldMap = (Hashtable)fGlobalMapStack.pop();
            Enumeration keys = oldMap.keys();
            while(keys.hasMoreElements()) {
                IdentityConstraint id = (IdentityConstraint)keys.nextElement();
                ValueStoreBase oldVal = (ValueStoreBase)oldMap.get(id);
                if(oldVal != null) {
                    ValueStoreBase currVal = (ValueStoreBase)fGlobalIDConstraintMap.get(id);
                    if (currVal == null)
                        fGlobalIDConstraintMap.put(id, oldVal);
                    else {
                        currVal.append(oldVal);
                        fGlobalIDConstraintMap.put(id, currVal);
                    }
                }
            }
        } // endElement()

        /**
         * Initializes the value stores for the specified element
         * declaration.
         */
        public void initValueStoresFor(XSElementDecl eDecl)
                throws XNIException {
            // initialize value stores for unique fields
            IdentityConstraint [] icArray = eDecl.fIDConstraints;
            int icCount = eDecl.fIDCPos;
            for (int i = 0; i < icCount; i++) {
                switch (icArray[i].getType()) {
                case (IdentityConstraint.UNIQUE):
                    // initialize value stores for unique fields
                    UniqueOrKey unique = (UniqueOrKey)icArray[i];
                    UniqueValueStore uniqueValueStore = (UniqueValueStore)fIdentityConstraint2ValueStoreMap.get(unique);
                    if (uniqueValueStore != null) {
                        // NOTE: If already initialized, don't need to
                        //       do it again. -Ac
                        continue;
                    }
                    uniqueValueStore = new UniqueValueStore(unique);
                    fValueStores.addElement(uniqueValueStore);
                    fIdentityConstraint2ValueStoreMap.put(unique, uniqueValueStore);
                    break;
                case (IdentityConstraint.KEY):
                    // initialize value stores for key fields
                    UniqueOrKey key = (UniqueOrKey)icArray[i];
                    KeyValueStore keyValueStore = (KeyValueStore)fIdentityConstraint2ValueStoreMap.get(key);
                    if (keyValueStore != null) {
                        // NOTE: If already initialized, don't need to
                        //       do it again. -Ac
                        continue;
                    }
                    keyValueStore = new KeyValueStore(key);
                    fValueStores.addElement(keyValueStore);
                    fIdentityConstraint2ValueStoreMap.put(key, keyValueStore);
                    break;
                case (IdentityConstraint.KEYREF):
                    // initialize value stores for key reference fields
                    KeyRef keyRef = (KeyRef)icArray[i];
                    KeyRefValueStore keyRefValueStore = (KeyRefValueStore)fIdentityConstraint2ValueStoreMap.get(keyRef);
                    if (keyRefValueStore != null) {
                        // NOTE: If already initialized, don't need to
                        //       do it again. -Ac
                        continue;
                    }
                    keyRefValueStore = new KeyRefValueStore(keyRef, null);
                    fValueStores.addElement(keyRefValueStore);
                    fIdentityConstraint2ValueStoreMap.put(keyRef, keyRefValueStore);
                    break;
                }
            }
        } // initValueStoresFor(XSElementDecl)

        /** Returns the value store associated to the specified field. */
        public ValueStoreBase getValueStoreFor(Field field) {
            IdentityConstraint identityConstraint = field.getIdentityConstraint();
            return (ValueStoreBase)fIdentityConstraint2ValueStoreMap.get(identityConstraint);
        } // getValueStoreFor(Field):ValueStoreBase

        /** Returns the value store associated to the specified IdentityConstraint. */
        public ValueStoreBase getValueStoreFor(IdentityConstraint id) {
            return (ValueStoreBase)fIdentityConstraint2ValueStoreMap.get(id);
        } // getValueStoreFor(IdentityConstraint):ValueStoreBase

        /** Returns the global value store associated to the specified IdentityConstraint. */
        public ValueStoreBase getGlobalValueStoreFor(IdentityConstraint id) {
            return (ValueStoreBase)fGlobalIDConstraintMap.get(id);
        } // getValueStoreFor(IdentityConstraint):ValueStoreBase
        // This method takes the contents of the (local) ValueStore
        // associated with id and moves them into the global
        // hashtable, if id is a <unique> or a <key>.
        // If it's a <keyRef>, then we leave it for later.
        public void transplant(IdentityConstraint id) {
            if (id.getType() == IdentityConstraint.KEYREF ) return;
            ValueStoreBase newVals = (ValueStoreBase)fIdentityConstraint2ValueStoreMap.get(id);
            fIdentityConstraint2ValueStoreMap.remove(id);
            ValueStoreBase currVals = (ValueStoreBase)fGlobalIDConstraintMap.get(id);
            if (currVals != null) {
                currVals.append(newVals);
                fGlobalIDConstraintMap.put(id, currVals);
            } else
                fGlobalIDConstraintMap.put(id, newVals);

        } // transplant(id)

        /** Check identity constraints. */
        public void endDocument() throws XNIException {

            int count = fValueStores.size();
            for (int i = 0; i < count; i++) {
                ValueStoreBase valueStore = (ValueStoreBase)fValueStores.elementAt(i);
                valueStore.endDocument();
            }

        } // endDocument()

        //
        // Object methods
        //

        /** Returns a string representation of this object. */
        public String toString() {
            String s = super.toString();
            int index1 = s.lastIndexOf('$');
            if (index1 != -1) {
                return s.substring(index1 + 1);
            }
            int index2 = s.lastIndexOf('.');
            if (index2 != -1) {
                return s.substring(index2 + 1);
            }
            return s;
        } // toString():String

    } // class ValueStoreCache

    // utility classes

    /**
     * Ordered hashtable. This class acts as a hashtable with
     * <code>put()</code> and <code>get()</code> operations but also
     * allows values to be queried via the order that they were
     * added to the hashtable.
     * <p>
     * <strong>Note:</strong> This class does not perform any error
     * checking.
     * <p>
     * <strong>Note:</strong> This class is <em>not</em> efficient but
     * is assumed to be used for a very small set of values.
     *
     * @author Andy Clark, IBM
     */
    static final class OrderedHashtable
        implements Cloneable {

        //
        // Data
        //

        /** Size. */
        private int fSize;

        /** Hashtable entries. */
        private Entry[] fEntries = null;

        //
        // Public methods
        //

        /** Returns the number of entries in the hashtable. */
        public int size() {
            return fSize;
        } // size():int

        /** Puts an entry into the hashtable. */
        public void put(Field key, IDValue value) {
            int index = indexOf(key);
            if (index == -1) {
                ensureCapacity(fSize);
                index = fSize++;
                fEntries[index].key = key;
            }
            fEntries[index].value = value;
        } // put(Field,String)

        /** Returns the value associated to the specified key. */
        public IDValue get(Field key) {
            return fEntries[indexOf(key)].value;
        } // get(Field):String

        /** Returns the index of the entry with the specified key. */
        public int indexOf(Field key) {
            for (int i = 0; i < fSize; i++) {
                // NOTE: Only way to be sure that the keys are the
                //       same is by using a reference comparison. In
                //       order to rely on the equals method, each
                //       field would have to take into account its
                //       position in the identity constraint, the
                //       identity constraint, the declaring element,
                //       and the grammar that it is defined in.
                //       Otherwise, you have the possibility that
                //       the equals method would return true for two
                //       fields that look *very* similar.
                //       The reference compare isn't bad, actually,
                //       because the field objects are cacheable. -Ac
                if (fEntries[i].key == key) {
                    return i;
                }
            }
            return -1;
        } // indexOf(Field):int

        /** Returns the key at the specified index. */
        public Field keyAt(int index) {
            return fEntries[index].key;
        } // keyAt(int):Field

        /** Returns the value at the specified index. */
        public IDValue valueAt(int index) {
            return fEntries[index].value;
        } // valueAt(int):String

        /** Removes all of the entries from the hashtable. */
        public void clear() {
            fSize = 0;
        } // clear()

        //
        // Private methods
        //

        /** Ensures the capacity of the entries array. */
        private void ensureCapacity(int size) {

            // sizes
            int osize = -1;
            int nsize = -1;

            // create array
            if (fEntries == null) {
                osize = 0;
                nsize = 2;
                fEntries = new Entry[nsize];
            }

            // resize array
            else if (fEntries.length <= size) {
                osize = fEntries.length;
                nsize = 2 * osize;
                Entry[] array = new Entry[nsize];
                System.arraycopy(fEntries, 0, array, 0, osize);
                fEntries = array;
            }

            // create new entries
            for (int i = osize; i < nsize; i++) {
                fEntries[i] = new Entry();
            }

        } // ensureCapacity(int)

        //
        // Cloneable methods
        //

        /** Clones this object. */
        public Object clone() {

            OrderedHashtable hashtable = new OrderedHashtable();
            for (int i = 0; i < fSize; i++) {
                hashtable.put(fEntries[i].key, fEntries[i].value);
            }
            return hashtable;

        } // clone():Object

        //
        // Object methods
        //

        /** Returns a string representation of this object. */
        public String toString() {
            if (fSize == 0) {
                return "[]";
            }
            StringBuffer str = new StringBuffer();
            str.append('[');
            for (int i = 0; i < fSize; i++) {
                if (i > 0) {
                    str.append(',');
                }
                str.append('{');
                str.append(fEntries[i].key);
                str.append(',');
                str.append(fEntries[i].value);
                str.append('}');
            }
            str.append(']');
            return str.toString();
        } // toString():String

        //
        // Classes
        //

        /**
         * Hashtable entry.
         */
        public static final class Entry {

            //
            // Data
            //

            /** Key. */
            public Field key;

            /** Value. */
            public IDValue value;

        } // class Entry

    } // class OrderedHashtable
} // class SchemaValidator
