package org.ecocean.security.dao;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.shiro.realm.jdbc.JdbcRealm;



/**
 * Sub-class of JdbcRealm that defines
 * the Data Source the JdbcRealm should
 * use
 * The configuration specified in web.xml
 * will cause an object of this class to
 * be injected into the SecurityManager
 * @author brucephillips
 *
 */
public class RoleSecurityJdbcRealm extends JdbcRealm {

	public RoleSecurityJdbcRealm() {

		super();

		//get the DataSource that JSecurity's JdbcRealm
		//should use to find the user's password
		//using the provided username
		//see context.xml for this DataSource's properties
        InitialContext ic;
        DataSource dataSource;
		try {

			ic = new InitialContext();
			
			//ic.
			
			dataSource = (DataSource) ic.lookup("java:/comp/env/jdbc/security");
			this.setDataSource(dataSource);

		} catch (NamingException e) {

			e.printStackTrace();

		}

	}

}
