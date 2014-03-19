/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Does not support passing of primitives as parameters to the methods that are
 * called
 *
 * @author Matt Roth
 */
public class JacksonGateway extends HttpServlet {

    private static final String SERVER_VO_PACKAGE = "com.tsr.remoting.valueobjects";
    private static final String ANDROID_VO_PACKAGE = "com.tsr.remoting.valueobjects";
    private static final String VO_CLASS = "vo_class";

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ObjectMapper mapper = new ObjectMapper();

        try {

            // get class and method of class from the request
            String theClass = request.getParameter("theClass");
            String method = request.getParameter("method");
            System.out.println("JacksonGateway request | " + theClass + " method " + method);
            // get the parameters for the method which have converted into Jackson json
            ArrayList params = mapper.readValue(request.getParameter("params"), ArrayList.class);

            // use reflection to create the class, call the method and convert result into json to be sent back in response
            Class<?> c = Class.forName(theClass);
            Object t = c.getConstructor(HttpServletRequest.class).newInstance(request);
            Class[] argTypes = new Class[params.size()];
            Object[] args = new Object[params.size()];

            for (int x = 0; x < params.size(); x++) {

                Object obj = unparseObjectFromJSON(params.get(x));

                argTypes[x] = obj.getClass();
                args[x] = obj;

            }
            Method main = c.getDeclaredMethod(method, argTypes);
            Object obj = main.invoke(t, args);
            String json = mapper.writeValueAsString(parseObjectForJSON(obj));

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json);

        } catch (Exception e) {

            String json = mapper.writeValueAsString(e.toString());

            response.setStatus(500);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json);
            e.printStackTrace();

        }
    }

    /**
     * Converts all VOs in the obj down every level from LinkedHashMaps to VOs
     *
     * @param obj
     * @return
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public Object unparseObjectFromJSON(Object obj) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        if (obj != null) {
            if (obj instanceof LinkedHashMap && ((LinkedHashMap) obj).get(VO_CLASS) != null) {

                return convertLinkedHashMapsToVO((LinkedHashMap) obj);

            } else if (obj instanceof List) {

                List list = (List) obj;
                for (int x = 0; x < list.size(); x++) {
                    list.set(x, unparseObjectFromJSON(list.get(x)));
                }
                return list;

            } else if (obj instanceof Map) {

                Map map = (Map) obj;
                Iterator entries = map.entrySet().iterator();
                while (entries.hasNext()) {
                    Entry e = (Entry) entries.next();
                    map.put(e.getKey(), unparseObjectFromJSON(e.getValue()));
                }
                return map;
            }
        }
        return obj;
    }

    /**
     * Converts LinkedHashMaps to VOs in the package SERVER_VO_PACKAGE when they
     * come for the android app and have the key/value pair for VO_CLASS
     *
     * Checks the values to see if they might be of type Map,List or a VO and
     * then converts
     *
     * @param lhm
     * @return
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private Object convertLinkedHashMapsToVO(LinkedHashMap lhm) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (lhm != null) {
            if (lhm.get(VO_CLASS) != null) {
                Class<?> vo = Class.forName(lhm.get(VO_CLASS).toString().replaceAll(ANDROID_VO_PACKAGE + ".", SERVER_VO_PACKAGE + "."));
                Object theVO = vo.newInstance();
                Field[] theFields = vo.getDeclaredFields();
                for (Field theField : theFields) {
                    String fieldKey = theField.getName();
                    Object fieldValue = lhm.get(fieldKey);
                    String setter = "set" + fieldKey.substring(0, 1).toUpperCase() + fieldKey.substring(1);
                    Class[] setterArgTypes = new Class[1];
                    Object[] setterArgs = new Object[1];
                    setterArgs[0] = fieldValue;
                    try {

                        if (fieldValue != null) {

                            fieldValue = unparseObjectFromJSON(fieldValue);

                            setterArgTypes[0] = fieldValue.getClass();

                            Method m = vo.getDeclaredMethod(setter, setterArgTypes);
                            m.invoke(theVO, setterArgs);

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        setterArgTypes[0] = Object.class;
                        try {
                            Method m = vo.getDeclaredMethod(setter, setterArgTypes);
                            m.invoke(theVO, setterArgs);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                return theVO;
            } else {
                return lhm;
            }
        } else {
            return lhm;
        }
    }

    /**
     * Convert all VOs all the way down every level to LinkedHashMaps with
     * custom field to determine what VO it is
     *
     * @param obj
     * @return
     */
    private Object parseObjectForJSON(Object obj) {
        if (obj != null) {
            boolean isVO = Package.getPackage(SERVER_VO_PACKAGE).equals(obj.getClass().getPackage());

            if (isVO) {

                return convertVOtoLinkedHashMap(obj);

            } else if (obj instanceof List) {

                List list = (List) obj;
                for (int x = 0; x < list.size(); x++) {
                    list.set(x, parseObjectForJSON(list.get(x)));
                }
                return list;

            } else if (obj instanceof Map) {

                Map map = (Map) obj;
                Iterator entries = map.entrySet().iterator();
                while (entries.hasNext()) {
                    Entry e = (Entry) entries.next();
                    map.put(e.getKey(), parseObjectForJSON(e.getValue()));
                }
                return map;

            } else {
                return obj;
            }
        } else {
            return obj;
        }
    }

    /**
     * Converts VOs to LinkedHashMaps before they go to the Android app and
     * checks VO values to see if they need to be converted
     *
     * @param obj
     * @return
     */
    private Object convertVOtoLinkedHashMap(Object obj) {
        if (obj != null) {
            boolean isVO = Package.getPackage(SERVER_VO_PACKAGE).equals(obj.getClass().getPackage());

            if (isVO) {

                LinkedHashMap lhm = new LinkedHashMap();
                Field[] theFields = obj.getClass().getDeclaredFields();
                for (Field theField : theFields) {
                    try {
                        String getter = "get" + theField.getName().substring(0, 1).toUpperCase() + theField.getName().substring(1);

                        Class c = obj.getClass();
                        Method m = c.getDeclaredMethod(getter, (Class[]) null);
                        Object o = m.invoke(obj, (Object[]) null);
                        lhm.put(theField.getName(), parseObjectForJSON(o));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                lhm.put(VO_CLASS, obj.getClass());
                return lhm;

            } else {
                return obj;
            }
        } else {
            return obj;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
