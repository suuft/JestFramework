package net.jest.reflect;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.jest.api.Controller;
import net.jest.api.Method;
import net.jest.api.RequiredAuth;
import net.jest.api.response.Response;
import net.jest.api.util.JsonUtil;
import net.jest.api.util.ResponseUtil;
import net.jest.request.Request;
import net.jest.util.CacheUtil;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static net.jest.util.DebugUtil.print;

@UtilityClass
public class MethodParser {

    // TODO: rewrite it
    public String getName(@NonNull Controller controller, @NonNull java.lang.reflect.Method method) {
        Method annotation = method.getDeclaredAnnotation(Method.class);

        if (annotation == null) return null;

        String path = controller.path() + annotation.name();

        return path;
    }

    public Function<Request, Response> parse(@NonNull Controller controller, @NonNull Object instance, @NonNull java.lang.reflect.Method method) {
        String path = getName(controller, method);

        if (path == null) return null;

        Map<String, Class<?>> parameters = new HashMap<>();


        for (Parameter parameter : method.getParameters()) {
            parameters.put(parameter.getAnnotation(net.jest.api.Parameter.class).value(), parameter.getType());
        }

        RequiredAuth auth = method.getDeclaredAnnotation(RequiredAuth.class);

        // i need rewrite it
        return request -> {

            if (auth != null
                    && CacheUtil.putIfAbsent(auth.value()) != null
                    && !CacheUtil.putIfAbsent(auth.value()).isAuthorized(request.getExchange(), request)) {
                return ResponseUtil.createResponse(ResponseUtil.UNAUTHORIZED, "Cant authorize your request. Please read api documentation. (The message is generated by the Jest framework)");
            }

            if (parameters.size() > request.getParameters().size()) return ResponseUtil.createResponse(ResponseUtil.BAD_REQUEST, "You have not specified enough parameters. Read the documentation. (The message is generated by the Jest framework)");

            AtomicReference<Response> response = new AtomicReference<>(null);
            List<Object> parametersInvoke = new ArrayList<>();

            parameters.forEach((name, type) -> {
                try
                {
                    if (request.getParameter(type, name) == null) response.set(ResponseUtil.createResponse(ResponseUtil.BAD_REQUEST, "The " + name + " argument was not found, or the wrong type was specified. (The message is generated by the Jest framework)"));
                    else parametersInvoke.add(request.getParameter(type, name));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    response.set(ResponseUtil.createResponse(ResponseUtil.INTERNAL_SERVER_ERROR, "Internal server error during process request. (The message is generated by the Jest framework)"));
                }
            });

            if (response.get() != null) return response.get();

            try
            {
                if (method.getReturnType().isAssignableFrom(Response.class) || method.getReturnType().equals(Response.class)) return (Response) (parametersInvoke.isEmpty() ? method.invoke(instance) : method.invoke(instance, parametersInvoke.toArray()));

                return ResponseUtil.createResponse(ResponseUtil.OK, method.invoke(instance, parametersInvoke.toArray(new Object[1])));
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return ResponseUtil.createResponse(ResponseUtil.INTERNAL_SERVER_ERROR, "Internal server error during method invoke. (The message is generated by the Jest framework)");
            }
        };
    }
}
