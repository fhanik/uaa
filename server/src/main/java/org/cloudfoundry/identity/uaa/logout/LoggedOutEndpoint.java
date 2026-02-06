package org.cloudfoundry.identity.uaa.logout;

import org.cloudfoundry.identity.uaa.util.UaaUrlUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class LoggedOutEndpoint {

    @GetMapping({"/logged_out", "/z/{subdomain}/logged_out"})
    public String loggedOut(HttpServletRequest request, Model model) {
        String pathPrefix = UaaUrlUtils.getZonePathPrefix(request);
        model.addAttribute("loginUrl", pathPrefix.isEmpty() ? "/login" : pathPrefix + "/login");
        return "logged_out";
    }

}
