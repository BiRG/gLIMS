package dslab.glims;

import java.io.IOException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

import dslab.glims.CredentialMediator.NoRefreshTokenException;

/**
 * Servlet handling the OAuth callback from the authentication service. We are
 * retrieving the OAuth code, then exchanging it for a refresh and an access
 * token and saving it.
 */
@SuppressWarnings("serial")
public class OAuth2CodeCallbackHandlerServlet extends GLIMSServlet {

	/** The name of the OAuth code URL parameter */
	public static final String CODE_URL_PARAM_NAME = "code";

	/** The name of the OAuth error URL parameter */
	public static final String ERROR_URL_PARAM_NAME = "error";

	/** The URL suffix of the servlet */
	public static final String URL_MAPPING = "/oauth2callback";

	/**
	 * The URL to redirect the user to after handling the callback. Consider
	 * saving this in a cookie before redirecting users to the Google
	 * authorization URL if you have multiple possible URL to redirect people
	 * to.
	 */
	public static String REDIRECT_URL = "/glims";

	// public static final String REDIRECT_URL =
	// "https://eddredit.appspot.com/eddredit";

	@SuppressWarnings("unused")
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		// Getting the "error" URL parameter
		String[] error = req.getParameterValues(ERROR_URL_PARAM_NAME);

		// Checking if there was an error such as the user denied access
		if (error != null && error.length > 0) {
			resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE,
					"There was an error: \"" + error[0] + "\".");
			return;
		}

		// Getting the "code" URL parameter
		String[] code = req.getParameterValues(CODE_URL_PARAM_NAME);

		// Checking conditions on the "code" URL parameter
		if (code == null || code.length == 0) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"The \"code\" URL parameter is missing");
			return;
		}

		// Construct incoming request URL
		// String requestUrl = getOAuthCodeCallbackHandlerUrl(req);

		StringBuffer fullUrlBuf = req.getRequestURL();
		if (req.getQueryString() != null) {
			fullUrlBuf.append('?').append(req.getQueryString());
		}

		AuthorizationCodeResponseUrl authResponse = new AuthorizationCodeResponseUrl(
				fullUrlBuf.toString());
		// check for user-denied error
		String authCode;
		if (authResponse.getError() != null) {
			// authorization denied...
			System.out.println("Access denied");
			return;
		} else {
			authCode = authResponse.getCode();
		}

		UserService userService = UserServiceFactory.getUserService();
		User user = userService.getCurrentUser();

		req.setAttribute("code", authCode);
		CredentialMediator mediator = getCredentialMediator(req, resp);
		try {
			mediator.getActiveCredential();
		} catch (NoRefreshTokenException e) {
			e.printStackTrace();
		}
		
		Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals("oauthRedirectUrl")) {
					REDIRECT_URL = cookie.getValue();
				}
			}
		}
		
		resp.sendRedirect(REDIRECT_URL);
		//req.getRequestDispatcher("/glims.jsp").forward(req, resp);
	}

}