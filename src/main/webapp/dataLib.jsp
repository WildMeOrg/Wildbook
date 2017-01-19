
<%!

public void sendAsJson(HttpServletResponse response, HttpServletRequest request, Object obj) throws IOException {
    	String json = new Gson().toJson(obj);

			String encodings = request.getHeader("Accept-Encoding");
			boolean useCompression = ((encodings != null) && (encodings.indexOf("gzip") > -1));

			if (!useCompression || (json.length() < 30)) {
				response.getWriter().write(json);
			} else {
    		OutputStream o = response.getOutputStream();
				response.setHeader("Content-Encoding", "gzip");
    		GZIPOutputStream gz = new GZIPOutputStream(o);
				gz.write(json.getBytes());
				gz.flush();
				gz.close();
				o.close();
			}


}


%>
