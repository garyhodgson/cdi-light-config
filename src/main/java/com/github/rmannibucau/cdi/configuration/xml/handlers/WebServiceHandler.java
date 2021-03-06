package com.github.rmannibucau.cdi.configuration.xml.handlers;

import com.github.rmannibucau.cdi.configuration.ConfigurationException;
import com.github.rmannibucau.cdi.configuration.loader.ClassLoaders;
import com.github.rmannibucau.cdi.configuration.model.ConfigBean;
import org.xml.sax.Attributes;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

public class WebServiceHandler extends NamespaceHandlerSupport {
    @Override
    public String supportedUri() {
        return "webservice";
    }

    @Override
    public ConfigBean createBean(final String localName, final Attributes attributes) {
        final String interfaceName = attributes.getValue("interface");

        final ConfigBean bean = new ConfigBean(localName, interfaceName, attributes.getValue("scope"), attributes.getValue("qualifier"), WebServiceFactory.class.getName(), "create", null, null, false);
        bean.getDirectAttributes().put("interfaceName", interfaceName);
        bean.getDirectAttributes().put("serviceQName", attributes.getValue("service-qname"));
        bean.getDirectAttributes().put("portQName", attributes.getValue("port-qname"));
        bean.getDirectAttributes().put("url", attributes.getValue("wsdl"));

        return bean;
    }

    public static class WebServiceFactory<T> {
        private String interfaceName;
        private QName serviceQName;
        private QName portQName;
        private URL url;

        public T create() {
            final Service service = Service.create(url, serviceQName);
            final Class<T> itf;
            try {
                itf = (Class<T>) ClassLoaders.tccl().loadClass(interfaceName);
            } catch (ClassNotFoundException e) {
                throw new ConfigurationException(e);
            }

            if (portQName != null) {
                return service.getPort(portQName, itf);
            }
            return service.getPort(itf);
        }
    }
}
