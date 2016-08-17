package de.launsch.coremedia.cae.view.errorhandling;

import com.coremedia.objectserver.view.*;
import lombok.Data;
import org.xml.sax.ContentHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.util.LinkedList;

/**
 * https://github.com/winniae/CoreMedia-ViewRecursionDecorator/
 * <p>
 * winfried.mosler@launsch.de
 * Licensed under CC0 Creative Commons Zero
 * <p>
 * This decorator for the CoreMedia CAE view template resolving processes
 * prevents endless recursions during the rendering.
 * It maintains a stack of all objects with their respective views and
 * stops if an object is rendered with the same view once again.
 * <p>
 * If your templates rely on request parameters for dispatching another template
 * you will need to restructure your template logic.
 * <p>
 * If a recursion is detected, a ViewException is rendered instead, which should
 * be easily readable in the Preview webapp but ignored in the Delivery webapp.
 * <p>
 * Add this to your Spring definition:
 * <pre>
 *   {@code
 * <customize:append id="addViewRecursionCatcherDecorator" bean="viewDecorators" enabled="${view.recursion.catcher.enabled:false}">
 * <list>
 * <bean class="de.launsch.coremedia.cae.view.errorhandling.ViewRecursionCatcherDecorator"/>
 * </list>
 * </customize:append>
 * </pre>
 * }
 */
public class ViewRecursionCatcherDecorator extends ViewDecoratorBase {

  private static final String REQUEST_ATTRIBUTE_VIEWSTACK = "viewstack";

  @Override
  protected Decorator getDecorator(View view) {
    return new ViewRecursionDetector();
  }

  private static class ViewRecursionDetector extends ViewDecoratorBase.Decorator {
    @Override
    public void decorate(ServletView decorated, Object bean, String view, HttpServletRequest request, HttpServletResponse response) {
      try {
        checkRecursion(request, bean, view);
        decorated.render(bean, view, request, response);
      } catch (RecursionException e) {
        throw e;
      } finally {
        clearCheckRecursion(request, bean, view);
      }
    }

    @Override
    public void decorate(TextView decorated, Object bean, String view, Writer out, HttpServletRequest request, HttpServletResponse response) {
      try {
        checkRecursion(request, bean, view);
        decorated.render(bean, view, out, request, response);
      } catch (RecursionException e) {
        throw e;
      } finally {
        clearCheckRecursion(request, bean, view);
      }
    }

    @Override
    public void decorate(XmlView decorated, Object bean, String view, ContentHandler out, HttpServletRequest request, HttpServletResponse response) {
      try {
        checkRecursion(request, bean, view);
        decorated.render(bean, view, out, request, response);
      } catch (RecursionException e) {
        throw e;
      } finally {
        clearCheckRecursion(request, bean, view);
      }
    }


    /**
     * Maintains a stack of all called views.
     * Throws an Exception when a recursion is detected
     *
     * @param httpServletRequest
     * @param bean
     * @param view
     * @throws RecursionException to indicate a (malicious) recursion.
     */
    void checkRecursion(HttpServletRequest httpServletRequest, Object bean, String view) throws RecursionException {
      LinkedList<BeanAndView> stack = (LinkedList<BeanAndView>) httpServletRequest.getAttribute(REQUEST_ATTRIBUTE_VIEWSTACK);

      if (stack == null) {
        stack = new LinkedList<BeanAndView>();
        httpServletRequest.setAttribute(REQUEST_ATTRIBUTE_VIEWSTACK, stack);
      }

      final BeanAndView newBeanAndView = new BeanAndView(bean, view);
      if (stack.contains(newBeanAndView)) {
        throw new RecursionException("Recursion detected, bean " + bean + " with view " + view + " was included already.", newBeanAndView);
      } else {
        stack.add(newBeanAndView);
      }
    }

    /**
     * Call after rendering the view to clear the stack
     *
     * @param httpServletRequest
     * @param bean
     * @param view
     */
    void clearCheckRecursion(HttpServletRequest httpServletRequest, Object bean, String view) {
      LinkedList<BeanAndView> stack = (LinkedList<BeanAndView>) httpServletRequest.getAttribute(REQUEST_ATTRIBUTE_VIEWSTACK);

      if (stack != null) {
        stack.remove(new BeanAndView(bean, view));
      }
    }
  }

  @Data
  private static class BeanAndView {
    private final Object bean;
    private final String view;
  }

  private static class RecursionException extends ViewException {
    RecursionException(String s, BeanAndView beanAndView) {
      super(s, null, beanAndView.bean, beanAndView.view, null);
    }
  }
}
