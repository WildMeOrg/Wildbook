/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.reijns.I3S;
/////////////////////////////////////////////////////////////////////
// Class
//    LinSys
// Purpose
//    Implementation of a Linear System
// Notes
//    1.  Probably should have m = number of rows = n = number of cols
//    2.  backSub assumes system is consistent with a unique solution

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class LinSys {
  private int n;          // Number of variables
  private int m;          // Number of equations
  private MatRow[] augMat;     // Augmented matrix of system
  private double[] x;          // Solution to system

  public final static double THRESHOLD = 1.0e-12;
  // Absolute values less
  // than this are treated as zero

  /////////////////////////////////////////////////////////////////////
  // Method
  //    Constructor
  // Note
  //    Number of variables is first arg, number of equations second!
  public LinSys(int newN, int newM) {
    n = newN;
    m = newM;
    augMat = new MatRow[m];
    for (int i = 0; i < m; i++)
      augMat[i] = new MatRow();
    x = new double[n];
  }  // Constructor


  /////////////////////////////////////////////////////////////////////
  // Method
  //    readInSystem
  // Note
  //    The right-hand side should follow the matrix in the input
  //    file.  For example, the system
  //       2x + y = 3
  //        x + y = 2
  //    should be stored as
  //       2 1
  //       1 1
  //       3 2
  public void readInSystem() {
    System.out.println("What's the name of the file storing the system?");
    Scanner sc = new Scanner(System.in);
    String filename = sc.next();

    try {
      FileReader sysFile = new FileReader(filename);
      // System.out.println("Created FileReader");
      Scanner sysSc = new Scanner(sysFile);
      // System.out.println("Created Scanner");

      for (int i = 0; i < m; i++)
        for (int j = 0; j < n; j++) {
          augMat[i].vals[j] = sysSc.nextDouble();
        }

      for (int i = 0; i < m; i++)
        augMat[i].vals[n] = sysSc.nextDouble();
    } catch (FileNotFoundException exception) {
      System.out.println(filename + " wasn't found.");
    } catch (IOException exception) {
      System.out.println(exception);
    }
  }  // readInSystem

  /////////////////////////////////////////////////////////////////////
  // Method
  //    generateSystem
  // Purpose
  //    Generate a random system whose solution is a vector of 1's.
  public void generateSystem() {
    for (int i = 0; i < m; i++)
      for (int j = 0; j < n; j++) {
        augMat[i].vals[j] = Math.random();
      }

    for (int i = 0; i < m; i++) {
      double sum = 0.0;
      for (int j = 0; j < n; j++)
        sum += augMat[i].vals[j];
      augMat[i].vals[n] = sum;
    }
  }  // generateSystem


  ///////////////////////////////////////////////////////////////////
  // Method
  //    gaussElim
  // Purpose
  //    Gaussian elimination with partial pivoting
  public void gaussElim() {
    int col = 0;
    int row = 0;
    int whichRow, i;
    double factor;
    while (row < m && col < n) {
      whichRow = findMax(row, col);  // Return negative if column of zeroes
      if (whichRow >= 0) {
        if (whichRow != row)
          swap(whichRow, row);
        factor = 1.0 / augMat[row].vals[col];

        multiply(factor, row, col);
        for (i = row + 1; i < m; i++) {
          factor = -augMat[i].vals[col];
          elim(i, factor, row, col);
        }
        row++;
        col++;
      } else {
        col++;
      }
    }  // while
  }  // gaussElim

  ///////////////////////////////////////////////////////////////////
  // Method
  //    gaussJordanElim1
  // Purpose
  //    Gauss-Jordan elimination with partial pivoting
  //    Version 1:  eliminate all off-diagonal entries in a
  //       column at once.
  public void gaussJordanElim1() {
    int col = 0;
    int row = 0;
    int whichRow, i;
    double factor;

    while (row < m && col < n) {
      whichRow = findMax(row, col);  // Return negative if column of zeroes
      if (whichRow >= 0) {
        if (whichRow != row)
          swap(whichRow, row);
        factor = 1.0 / augMat[row].vals[col];

        multiply(factor, row, col);
        for (i = 0; i < m; i++) {
          if (i != row) {
            factor = -augMat[i].vals[col];
            elim(i, factor, row, col);
          }
        }
        row++;
        col++;
      } else {
        col++;
      }
    }  // while
  }  // gaussJordanElim1


  ///////////////////////////////////////////////////////////////////
  // Method
  //    gaussJordanElim2
  // Purpose
  //    Gauss-Jordan elimination with partial pivoting
  //    Version 2:  First convert the matrix to upper-triangular
  //       form.  Then eliminate entries above diagonal.
  public void gaussJordanElim2() {

    gaussElim();

    int i, j;
    double factor;

    System.out.println("After Gauss:");
    printMat();

    for (j = n - 1; j >= 1; j--) {
      for (i = 0; i < j; i++) {
        factor = augMat[i].vals[j];
        augMat[i].vals[n] -= augMat[j].vals[n] * factor;
        augMat[i].vals[j] = 0.0;
      }
    }
  }  // gaussJordanElim2


  ///////////////////////////////////////////////////////////////////
  // Method
  //    backSub
  // Purpose
  //    Solve a triangular system by back substitution
  public void backSub() {
    int i, j;
    double sum;

    for (i = m - 1; i >= 0; i--) {
      sum = augMat[i].vals[n];
      for (j = i + 1; j < n; j++)
        sum -= augMat[i].vals[j] * x[j];
      x[i] = sum / augMat[i].vals[i];
    }
  }  // backSub


  ///////////////////////////////////////////////////////////////////
  // Method
  //    swap
  // Purpose
  //    swap two rows of the matrix
  private void swap(int firstRow, int secondRow) {
    MatRow temp = augMat[firstRow];
    augMat[firstRow] = augMat[secondRow];
    augMat[secondRow] = temp;
  }  // swap


  ///////////////////////////////////////////////////////////////////
  // Method
  //    multiply
  // Purpose
  //    Multiply a row of the matrix by a constant
  private void multiply(double factor, int row) {
    for (int j = 0; j <= n; j++)
      augMat[row].vals[j] *= factor;
  }  // multiply


  ///////////////////////////////////////////////////////////////////
  // Method
  //    elim
  // Purpose
  //    Add a constant multiple factor of row r to row i starting with
  //       column c
  private void elim(int i, double factor, int r, int c) {
    augMat[i].vals[c] = 0.0;
    for (int j = c + 1; j <= n; j++) {
      augMat[i].vals[j] += factor * augMat[r].vals[j];
    }
  }  // elim


  ///////////////////////////////////////////////////////////////////
  // Method
  //    multiply
  // Purpose
  //    Multiply a row of the matrix by a constant starting with
  //       a particular column (assume preceding entries are zero)
  private void multiply(double factor, int row, int col) {
    for (int j = col; j <= n; j++)
      augMat[row].vals[j] *= factor;
  }  // multiply


  ///////////////////////////////////////////////////////////////////
  // Method
  //    findMax
  // Purpose
  //    Find row with maximum absolute value in current column
  //    Return -1 if column of zeroes
  public int findMax(int row, int col) {
    int currMaxRow = row;
    double currMaxVal = Math.abs(augMat[row].vals[col]);
    for (int i = row + 1; i < m; i++)
      if (Math.abs(augMat[i].vals[col]) > currMaxVal) {
        currMaxVal = Math.abs(augMat[i].vals[col]);
        currMaxRow = i;
      }

    if (currMaxVal < THRESHOLD)
      return -1;  // Column of zeroes
    else
      return currMaxRow;
  }  // findMax


  ///////////////////////////////////////////////////////////////////
  // Method
  //    printMat
  // Purpose
  //    Print augmented matrix
  public void printMat() {
    for (int i = 0; i < m; i++) {
      for (int j = 0; j <= n; j++)
        System.out.print(augMat[i].vals[j] + " ");
      System.out.println();
    }
  }  // printMat


  ///////////////////////////////////////////////////////////////////
  // Method
  //    printSoln
  // Purpose
  //    Print solution
  public void printSoln() {
    for (int i = 0; i < m; i++)
      System.out.print(x[i] + " ");
    System.out.println();
  }  // printSoln


  ///////////////////////////////////////////////////////////////////
  // Class
  //    MatRow
  // Purpose
  //    Store a single row of the augmented matrix
  // Note
  //    This is a private inner class.  So only the methods
  //    of LinSys can access its public members.
  private class MatRow {
    public double[] vals;

    ////////////////////////////////////////////////////////////////
    // Method
    //    Constructor
    public MatRow() {
      vals = new double[n + 1];
    }  // Constructor

    ////////////////////////////////////////////////////////////////
    // Method
    //    toString
    public String toString() {
      String s = "";
      for (int i = 0; i <= n; i++)
        s += (vals[i] + " ");
      return s;
    }  // toString
  }  // MatRow
}  // class LinSys
