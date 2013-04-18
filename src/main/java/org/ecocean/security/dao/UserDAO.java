package org.ecocean.security.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.ecocean.servlet.ServletUtilities;

import org.ecocean.User;

//import name.brucephillips.somesecurity.model.*;

/**
 * Static methods to get either all users
 * or a specific user by username
 * @author brucephillips
 *
 */
public class UserDAO
{

    /**
     * Get a specific user from the data store
     * @param userName
     * @return collection of User objects
     */
    public static List<User> getUser(String userName)
    {
        
      List <User>usersList = new ArrayList<User>();
      ResultSet rs = null;
      PreparedStatement ps = null;
      
        try{
            //ConnectionPool pool = ConnectionPool.getInstance();
            Connection connection = ServletUtilities.getConnection();
            
            

        
            String query = "SELECT * FROM users " +
                       "WHERE username = ?";
          
            ps = connection.prepareStatement(query);
            ps.setString(1, userName);
            rs = ps.executeQuery();
            while (rs.next()){
                User user = new User();
                user.setUsername(rs.getString("username"));
                user.setUserID(Long.valueOf(rs.getString("userid") ) );
                user.setPassword(rs.getString("password") );

                usersList.add(user);
            }
            return usersList;
        }
        catch (SQLException e){
            e.printStackTrace();
            return null;
        }
        finally
        {
            DBUtil.closeResultSet(rs);
            DBUtil.closePreparedStatement(ps);
            //pool.freeConnection(connection);
        }
    }

    /**
     * Get all users from the data store
     * @return collection of User objects
     */
    public static List <User> getAllUsers()
    {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection connection = pool.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;

        List <User>usersList = new ArrayList<User>();

        String query = "SELECT * FROM users ";
        try
        {
            ps = connection.prepareStatement(query);
            rs = ps.executeQuery();

            while (rs.next())
            {
                User user = new User();
                user.setUsername(rs.getString("username"));
                user.setUserID(Long.valueOf(rs.getString("userid") ) );
                user.setPassword(rs.getString("password") );
                usersList.add(user);
            }
            return usersList;
        }
        catch (SQLException e){
            e.printStackTrace();
            return null;
        }
        finally
        {
            DBUtil.closeResultSet(rs);
            DBUtil.closePreparedStatement(ps);
            pool.freeConnection(connection);
        }
    }
}