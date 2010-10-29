/*
 * $URL$
 * $Revision$
 * $Author$
 *
 * Copyright (C) 2007-2010 Joshua Shinavier
 */


package net.fortytwo.linkeddata;

import net.fortytwo.flow.rdf.RDFBuffer;
import net.fortytwo.flow.rdf.RDFSink;
import net.fortytwo.flow.rdf.SesameInputAdapter;
import net.fortytwo.flow.rdf.SingleContextPipe;
import net.fortytwo.linkeddata.dereferencers.FileURIDereferencer;
import net.fortytwo.linkeddata.dereferencers.HTTPURIDereferencer;
import net.fortytwo.linkeddata.dereferencers.JarURIDereferencer;
import net.fortytwo.linkeddata.rdfizers.ImageRdfizer;
import net.fortytwo.linkeddata.rdfizers.VerbatimRdfizer;
import net.fortytwo.linkeddata.sail.LinkedDataSail;
import net.fortytwo.ripple.Ripple;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.StringUtils;
import net.fortytwo.ripple.URIMap;
import net.fortytwo.ripple.util.BNodeToURIFilter;
import net.fortytwo.ripple.util.RDFUtils;
import org.apache.log4j.Logger;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.sail.Sail;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;
import org.restlet.data.MediaType;
import org.restlet.resource.Representation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * A configurable container of URI dereferencers and RDFizers which provides a unified view of the Web as a collection of RDF documents.
 * <p/>
 * Note: this tool stores metadata about web activity; if a suitable
 * dereferencer cannot be found for a URI, no metadata will be stored.
 * <p/>
 * Author: josh
 * Date: Jan 16, 2008
 * Time: 12:25:29 PM
 */
public class WebClosure {
    // TODO: these should probably not be HTTP URIs
    public static final String
            CACHE_NS = "http://fortytwo.net/2008/01/webclosure#",
            CACHE_CONTEXT = CACHE_NS + "context",
            CACHE_MEMO = CACHE_NS + "memo",
            FULL_MEMO = CACHE_NS + "fullMemo";

    private static final String[] BADEXT = {
            "123", "3dm", "3dmf", "3gp", "8bi", "aac", "ai", "aif", "app", "asf",
            "asp", "asx", "avi", "bat", "bin", "bmp", "c", "cab", "cfg", "cgi",
            "com", "cpl", "cpp", "css", "csv", "dat", "db", "dll", "dmg", "dmp",
            "doc", "drv", "drw", "dxf", "eps", "exe", "fnt", "fon", "gif", "gz",
            "h", "hqx", "htm", "html", "iff", "indd", "ini", "iso", "java", /*"jpeg",*/
            /*"jpg",*/ "js", "jsp", "key", "log", "m3u", "mdb", "mid", "midi", "mim",
            "mng", "mov", "mp3", "mp4", "mpa", "mpg", "msg", "msi", "otf", "pct",
            "pdf", "php", "pif", "pkg", "pl", "plugin", "png", "pps", "ppt", "ps",
            "psd", "psp", "qt", "qxd", "qxp", "ra", "ram", "rar", "reg", "rm",
            "rtf", "sea", "sit", "sitx", "sql", "svg", "swf", "sys", "tar", "tif",
            "ttf", "uue", "vb", "vcd", "wav", "wks", "wma", "wmv", "wpd", "wps",
            "ws", "xhtml", "xll", "xls", "yps", "zip"};

    private static final Logger LOGGER = Logger.getLogger(WebClosure.class);

    private final WebCache cache;

    // Maps media types to Rdfizers
    private final Map<MediaType, MediaTypeInfo> rdfizers
            = new HashMap<MediaType, MediaTypeInfo>();

    // Maps URI schemes to Dereferencers
    private final Map<String, Dereferencer> dereferencers = new HashMap<String, Dereferencer>();

    private final URIMap uriMap;
    private final ValueFactory valueFactory;
    private final boolean useBlankNodes;

    private String acceptHeader = null;

    public static WebClosure createDefault(final Sail baseSail,
                                           final URIMap uriMap) throws RippleException {
        try {
            SailConnection sc = baseSail.getConnection();
            try {
                int maxCacheCapacity = Ripple.getProperties().getInt(LinkedDataSail.MAX_CACHE_CAPACITY);

                WebCache cache = new WebCache(maxCacheCapacity, baseSail.getValueFactory());
                WebClosure wc = new WebClosure(cache, uriMap, baseSail.getValueFactory());

                // Add URI dereferencers.
                HTTPURIDereferencer hdref = new HTTPURIDereferencer(wc);
                for (String aBADEXT : BADEXT) {
                    hdref.blackListExtension(aBADEXT);
                }
                wc.addDereferencer("http", hdref);
                wc.addDereferencer("jar", new JarURIDereferencer());
                wc.addDereferencer("file", new FileURIDereferencer());

                // Add rdfizers.
                wc.addRdfizer(RDFUtils.findMediaType(RDFFormat.RDFXML), new VerbatimRdfizer(RDFFormat.RDFXML));
                wc.addRdfizer(RDFUtils.findMediaType(RDFFormat.TURTLE), new VerbatimRdfizer(RDFFormat.TURTLE));
                wc.addRdfizer(RDFUtils.findMediaType(RDFFormat.N3), new VerbatimRdfizer(RDFFormat.N3), 0.9);
                wc.addRdfizer(RDFUtils.findMediaType(RDFFormat.TRIG), new VerbatimRdfizer(RDFFormat.TRIG), 0.8);
                wc.addRdfizer(RDFUtils.findMediaType(RDFFormat.TRIX), new VerbatimRdfizer(RDFFormat.TRIX), 0.8);
                wc.addRdfizer(RDFUtils.findMediaType(RDFFormat.NTRIPLES), new VerbatimRdfizer(RDFFormat.NTRIPLES), 0.5);
                Rdfizer imageRdfizer = new ImageRdfizer();
                // Mainstream EXIF-compatible image types: JPEG, TIFF
                wc.addRdfizer(MediaType.IMAGE_JPEG, imageRdfizer, 0.4);
                wc.addRdfizer(new MediaType("image/tiff"), imageRdfizer, 0.4);
                wc.addRdfizer(new MediaType("image/tiff-fx"), imageRdfizer, 0.4);
                // TODO: add an EXIF-based Rdfizer for RIFF WAV audio files

                // Don't bother trying to dereference terms in these common namespaces.
                wc.addMemo("http://www.w3.org/XML/1998/namespace#", new ContextMemo(ContextMemo.Status.Ignored), sc);
                wc.addMemo("http://www.w3.org/2001/XMLSchema", new ContextMemo(ContextMemo.Status.Ignored), sc);
                wc.addMemo("http://www.w3.org/2001/XMLSchema#", new ContextMemo(ContextMemo.Status.Ignored), sc);

                // Don't try to dereference the cache index.
                wc.addMemo("http://fortytwo.net/2007/08/ripple/cache#", new ContextMemo(ContextMemo.Status.Ignored), sc);

                return wc;
            } finally {
                sc.close();
            }
        } catch (SailException e) {
            throw new RippleException(e);
        }
    }

    /**
     * @param cache        caching metadata
     * @param uriMap       mapping for virtual URI spaces
     * @param valueFactory factory for URI and literal objects
     */
    public WebClosure(final WebCache cache,
                      final URIMap uriMap,
                      final ValueFactory valueFactory) throws RippleException {
        this.cache = cache;
        this.valueFactory = valueFactory;
        this.uriMap = uriMap;
        useBlankNodes = Ripple.getProperties().getBoolean(Ripple.USE_BLANK_NODES);
    }

    public URIMap getURIMap() {
        return uriMap;
    }

    public String getAcceptHeader() {
        if (null == acceptHeader) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            // Order from highest quality to lowest.
            Comparator<MediaTypeInfo> comparator
                    = new Comparator<MediaTypeInfo>() {
                public int compare(final MediaTypeInfo first,
                                   final MediaTypeInfo second) {
                    return first.quality < second.quality ? 1 : first.quality > second.quality ? -1 : 0;
                }
            };

            MediaTypeInfo[] array = new MediaTypeInfo[rdfizers.size()];
            rdfizers.values().toArray(array);
            Arrays.sort(array, comparator);

            for (MediaTypeInfo m : array) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }

                sb.append(m.mediaType.getName());
                double quality = m.quality;
                if (1.0 != quality) {
                    sb.append(";q=").append(quality);
                }
            }

            acceptHeader = sb.toString();
        }

        return acceptHeader;
    }

    public void addRdfizer(final MediaType mediaType,
                           final Rdfizer rdfizer,
                           final double qualityFactor) {
        if (qualityFactor <= 0 || qualityFactor > 1) {
            throw new IllegalArgumentException("quality factor must be between 0 and 1");
        }

        MediaTypeInfo rq = new MediaTypeInfo();
        rq.mediaType = mediaType;
        rq.quality = qualityFactor;
        rq.rdfizer = rdfizer;
        rdfizers.put(mediaType, rq);

        acceptHeader = null;
    }

    public void addRdfizer(final MediaType mediaType, final Rdfizer rdfizer) {
        addRdfizer(mediaType, rdfizer, 1.0);
    }

    public void addDereferencer(final String scheme, final Dereferencer uriDereferencer) {
        dereferencers.put(scheme, uriDereferencer);
    }

    public void addMemo(final String uri,
                        final ContextMemo memo,
                        final SailConnection sc) throws RippleException {
        cache.setMemo(uri, memo, sc);
    }

    public ContextMemo.Status extendTo(final URI nonInfoURI,
                                       final RDFSink<RippleException> resultSink,
                                       final SailConnection sc) throws RippleException {
        // TODO: memos should be inferred in a scheme-specific way
        String defrag = RDFUtils.removeFragmentIdentifier(nonInfoURI.toString());

        ContextMemo memo;
        Dereferencer dref;

        // Note: this URL should be treated as a "black box" once created; it
        // need not resemble the URI it was created from.
        String mapped;

        // Rules out an otherwise possible race condition
        synchronized (cache) {
            memo = cache.getMemo(defrag, sc);

            if (null != memo) {
                // Don't log success or failure based on cached values.
                return memo.getStatus();
            }

            try {
                mapped = uriMap.get(defrag);
            }

            catch (RippleException e) {
                // Don't log extremely common errors.
                return ContextMemo.Status.InvalidUri;
            }

            try {
                dref = chooseDereferencer(mapped);
            }

            catch (RippleException e) {
                e.logError(false);

                // Don't log extremely common errors.
                return ContextMemo.Status.InvalidUri;
            }

            if (null == dref) {
                // Don't log extremely common errors.
                return ContextMemo.Status.BadUriScheme;
            }

            LOGGER.info("Dereferencing URI <"
                    + StringUtils.escapeURIString(nonInfoURI.toString()) + ">");
            //+ " at location " + mapped );

            memo = new ContextMemo(ContextMemo.Status.Success);
            cache.setMemo(defrag, memo, sc);
        }

        memo.setUriDereferencer(dref);

        // Note: from this point on, failures are explicitly stored as caching
        // metadata.

        Representation rep;

        try {
            rep = dref.dereference(mapped);
        }

        catch (RippleException e) {
            e.logError();
            memo.setStatus(ContextMemo.Status.DereferencerError);
            return logStatus(nonInfoURI, memo.getStatus());
        }

        catch (Throwable t) {
            memo.setStatus(ContextMemo.Status.DereferencerError);
            logStatus(nonInfoURI, memo.getStatus());
            throw new RippleException(t);
        }

        MediaType mt;

        try {
            mt = rep.getMediaType();
        }

        catch (Throwable t) {
            throw new RippleException(t);
        }

//System.out.println( "media type = " + mt );
        memo.setMediaType(mt);

        Rdfizer rfiz = chooseRdfizer(mt);
        if (null == rfiz) {
            memo.setStatus(ContextMemo.Status.BadMediaType);
            memo.setMediaType(mt);
            return logStatus(nonInfoURI, memo.getStatus());
        }

        memo.setRdfizer(rfiz);

        URI context;

        try {
            context = valueFactory.createURI(defrag);
        }

        catch (Throwable t) {
            throw new RippleException(t);
        }

        // Note: any pre-existing context information is discarded.
        RDFSink<RippleException> scp = new SingleContextPipe(resultSink, context, valueFactory);

        RDFBuffer<RippleException> results = new RDFBuffer<RippleException>(scp);
        RDFHandler hdlr = new SesameInputAdapter(useBlankNodes
                ? results
                : new BNodeToURIFilter(results, valueFactory));

        InputStream is;

        try {
            is = rep.getStream();
        }

        catch (IOException e) {
            throw new RippleException(e);
        }

        catch (Throwable t) {
            throw new RippleException(t);
        }

        // For now...
        String baseUri = defrag;

        ContextMemo.Status status;

        status = rfiz.handle(is, hdlr, nonInfoURI, baseUri);

        if (ContextMemo.Status.Success == status) {
            // Push results and record success
            results.flush();
        }

        memo.setStatus(status);

        return logStatus(nonInfoURI, status);
    }

    private ContextMemo.Status logStatus(final URI uri, final ContextMemo.Status status) {
        if (ContextMemo.Status.Success != status) {
            LOGGER.info("Failed to dereference URI <"
                    + StringUtils.escapeURIString(uri.toString()) + ">: " + status);
        }

        return status;
    }

    private Dereferencer chooseDereferencer(final String uri) throws RippleException {
        String scheme;

        try {
            scheme = new java.net.URI(uri).getScheme();
        }

        catch (URISyntaxException e) {
            throw new RippleException(e);
        }

        return dereferencers.get(scheme);
    }

    private Rdfizer chooseRdfizer(final MediaType mediaType) throws RippleException {
        MediaTypeInfo rq = rdfizers.get(mediaType);
        return (null == rq) ? null : rq.rdfizer;
    }

    private class MediaTypeInfo {
        MediaType mediaType;
        public double quality;
        public Rdfizer rdfizer;
    }
}