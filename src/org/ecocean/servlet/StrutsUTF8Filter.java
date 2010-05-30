package org.ecocean.servlet;

import java.io.IOException;
import javax.servlet.*;

public class StrutsUTF8Filter implements Filter
{
	public void destroy() {}

	public void doFilter(ServletRequest request,
		ServletResponse response, FilterChain chain)
		throws IOException, ServletException
	{
		request.setCharacterEncoding("UTF-8");
		chain.doFilter(request, response);
	}

	public void init(FilterConfig filterConfig)
		throws ServletException
	{
	}
}
