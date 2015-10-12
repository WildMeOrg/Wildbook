package fap.custom;

/**
 * Callback interface. Declares common methods for callback objects.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */



public class WildbookFAPCallback implements fap.core.util.Callback {
  
  private int numCallbacks=0;

  /**
   * Indicates how many callbacks we want.
   * @return number of callbacks
   */
  public int getCallbackCount(){
    return numCallbacks;
  }
  
  /**
   * Sets the number of callbacks.
   * @param cbcount number of callbacks
   */
  public void setCallbackCount(int cbcount){numCallbacks=cbcount;}
  
  /**
   * Initializes the callback object.
   * @param value initialization value for the callback object
   */
  public void init(int value){}
  
  /**
   * Callback method.
   * @throws Exception if an error occurs
   */
  public void callback() {}
  
}
