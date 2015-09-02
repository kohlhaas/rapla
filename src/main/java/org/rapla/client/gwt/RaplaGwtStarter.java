package org.rapla.client.gwt;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import org.rapla.framework.RaplaException;
import org.rapla.gwtjsonrpc.client.ExceptionDeserializer;
import org.rapla.gwtjsonrpc.client.impl.AbstractJsonProxy;
import org.rapla.gwtjsonrpc.client.impl.EntryPointFactory;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RaplaExceptionDeserializer;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Christopher on 02.09.2015.
 */
public class RaplaGwtStarter
{
    public static final String LOGIN_COOKIE = "raplaLoginToken";

    private void setProxy()
    {
        AbstractJsonProxy.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(Class serviceClass)
            {
                String name = serviceClass.getName().replaceAll("_JsonProxy", "");
                String url = GWT.getModuleBaseURL() + "../rapla/json/" + name;
                return url;
            }
        });
        AbstractJsonProxy.setExceptionDeserializer(new ExceptionDeserializer()
        {
            @Override public Exception deserialize(String exception, String message, List<String> parameter)
            {
                final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
                final RaplaException deserializedException = raplaExceptionDeserializer.deserializeException(exception, message, parameter);
                return deserializedException;
            }
        });
    }

    private LoginTokens getValidToken()
    {
        final Logger logger = Logger.getLogger("componentClass");
        String tokenString = Cookies.getCookie(LOGIN_COOKIE);
        if (tokenString != null)
        {
            // re request the server for refresh token
            LoginTokens token = LoginTokens.fromString(tokenString);
            boolean valid = token.isValid();
            if (valid)
            {
                logger.log(Level.INFO, "found valid cookie: " + tokenString);
                return token;
            }
        }
        logger.log(Level.INFO, "No valid login token found");
        return null;
    }

    public void startApplication()
    {
        setProxy();
        RootPanel.get("raplaPopup").setVisible(false);

        LoginTokens token = getValidToken();
        if (token != null)
        {
            //            final Document doc = Document.Util.getDocument();
            //            final HTMLElement titleBar = doc.getElementById("title");
            //            titleBar.setInnerHTML("Hallo Rapla");
            //            final HTMLElement button = doc.getElementById("send");
            //            button.setInnerHTML("Los!");
            //            logger.info("Hallo welt");
            //;
            //final MyEventListener func = new MyEventListener(titleBar);
            //button.setOnclick(func);
            //            button.addEventListener("click", (Event e) -> {
            //                        HTMLElement name = doc.getElementById("name");
            //                        String val = name.getValue();
            //                        titleBar.setInnerText("Hallo " + val);
            //                    }, true
            //            );
            AbstractJsonProxy.setAuthThoken(token.getAccessToken());
            Scheduler.get().scheduleFixedDelay(new Scheduler.RepeatingCommand()
            {
                @Override public boolean execute()
                {
                    final MainInjector injector = GWT.create(MainInjector.class);
                    Bootstrap bootstrap = injector.getBootstrap();
                    bootstrap.load();
                    return false;
                }
            }, 100);
        }
        else
        {
            Window.Location.replace(GWT.getModuleBaseURL() + "../rapla?page=auth");
        }
    }

}
