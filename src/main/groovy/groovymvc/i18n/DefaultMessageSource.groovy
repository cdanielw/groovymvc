package groovymvc.i18n

import javax.servlet.ServletContext
import java.text.MessageFormat
import java.util.ResourceBundle.Control
/**
 * @author Daniel Wiell
 */
class DefaultMessageSource implements MessageSource {
    private final Control control
    private final String[] baseNames

    private DefaultMessageSource(ServletContext servletContext, boolean reloadable, String... baseNames) throws MissingResourceException {
        control = new Utf8Control(servletContext, reloadable)
        this.baseNames = (baseNames as List<String>).unique() as String[]
        this.baseNames.each { String baseName ->
            getBundle(baseName, Locale.default)
        }
    }

    static DefaultMessageSource reloadableMessageSource(ServletContext servletContext = null, String... baseNames) {
        new DefaultMessageSource(servletContext, true, baseNames)
    }

    static DefaultMessageSource messageSource(ServletContext servletContext = null, String... baseNames) {
        new DefaultMessageSource(servletContext, false, baseNames)
    }

    String message(String key, String... arguments) {
        message(key, Locale.default, arguments)
    }

    String message(String key, Locale locale, String... arguments) {
        for (String baseName : baseNames) {
            def message = getBundleMessage(baseName, key, locale)
            if (message != null)
                return applyArguments(message, arguments)
        }
        return key
    }

    boolean contains(String key, Locale locale) {
        baseNames.any { String baseName ->
            getBundle(baseName, locale)?.containsKey(key)
        }
    }

    private String applyArguments(String message, String... arguments) {
        MessageFormat.format(message, arguments)
    }

    private String getBundleMessage(String baseName, String key, Locale locale) {
        ResourceBundle bundle = getBundle(baseName, locale)
        try {
            return bundle.getString(key)
        } catch (MissingResourceException ignore) {
            return null
        }
    }

    private ResourceBundle getBundle(String baseName, Locale locale) {
        ResourceBundle.getBundle(baseName, locale, this.class.classLoader, control)
    }


    private static class Utf8Control extends Control {
        private final boolean reloadable
        private final ServletContext servletContext

        Utf8Control(ServletContext servletContext, boolean reloadable) {
            this.servletContext = servletContext
            this.reloadable = reloadable
        }

        ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) {
            String bundleName = toBundleName(baseName, locale)
            final String resourceName = toResourceName(bundleName, "properties")

            def bundle = null
            if (servletContext)
                bundle = getOrReloadBundle(servletContext, '/WEB-INF/' + resourceName, reload)
            if (!bundle && servletContext)
                bundle = getOrReloadBundle(servletContext, '/WEB-INF/classes/' + resourceName, reload)
            if (!bundle)
                bundle = getOrReloadBundle(loader, resourceName, reload)
            return bundle
        }

        private ResourceBundle getOrReloadBundle(resourceRepository, String resourceName, boolean reload) {
            final boolean reloadFlag = reload
            ResourceBundle bundle = null
            InputStream stream = null
            if (reloadFlag) {
                URL url = resourceRepository.getResource(resourceName)
                if (url != null) {
                    URLConnection connection = url.openConnection()
                    if (connection != null) {
                        // Disable caches to get fresh data for reloading.
                        connection.useCaches = false
                        stream = connection.inputStream
                    }
                }
            } else {
                stream = resourceRepository.getResourceAsStream(resourceName)
            }
            if (stream != null) {
                try {
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream))
                } finally {
                    stream.close()
                }
            }
            return bundle
        }

        List<String> getFormats(String baseName) {
            Collections.unmodifiableList(Arrays.asList("java.properties"))
        }

        long getTimeToLive(String baseName, Locale locale) {
            return reloadable ? 100 : TTL_NO_EXPIRATION_CONTROL
        }

        public boolean needsReload(String baseName, Locale locale,
                                   String format, ClassLoader loader,
                                   ResourceBundle bundle, long loadTime) {
            def bundleName = toBundleName(baseName, locale)
            if (servletContext && servletContext.getResource("/WEB-INF/${bundleName}.properties"))
                return servletResourceNeedsReload(servletContext, "/WEB-INF/${bundleName}.properties", loadTime)
            if (servletContext && servletContext.getResource("/WEB-INF/classes/${bundleName}.properties"))
                return servletResourceNeedsReload(servletContext, "/WEB-INF/classes/${bundleName}.properties", loadTime)
            else
                return super.needsReload(baseName, locale, format, loader, bundle, loadTime)
        }

        private boolean servletResourceNeedsReload(ServletContext servletContext,
                                                   String resourceName, long loadTime) {
            boolean result = false
            try {
                URL url = servletContext.getResource(resourceName)
                if (url != null) {
                    long lastModified = 0
                    URLConnection connection = url.openConnection()
                    if (connection != null) {
                        // disable caches to get the correct data
                        connection.setUseCaches(false)
                        lastModified = connection.getLastModified()
                    }
                    result = lastModified >= loadTime
                }
            } catch (NullPointerException npe) {
                throw npe
            } catch (Exception ignore) {
                // ignore other exceptions
            }
            return result
        }
    }
}