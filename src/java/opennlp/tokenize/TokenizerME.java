///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2002 Jason Baldridge and Gann Bierner
// 
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
// 
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////

package opennlp.tokenize;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import opennlp.common.util.Span;
import opennlp.common.util.ObjectIntPair;
import opennlp.common.util.PerlHelp;

import opennlp.maxent.MaxentModel;
import opennlp.maxent.EventStream;
import opennlp.maxent.EventCollectorAsStream;
import opennlp.maxent.ContextGenerator;
import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;

import java.util.List;
import java.util.ArrayList;

/**
 * A Tokenizer for converting raw text into separated tokens.  It uses
 * Maximum Entropy to make its decisions.  The features are loosely
 * based off of Jeff Reynar's UPenn thesis "Topic Segmentation:
 * Algorithms and Applications.", which is available from his
 * homepage: <http://www.cis.upenn.edu/~jcreynar>.
 *
 * @author      Tom Morton
 * @version $Revision: 1.2 $, $Date: 2003/04/07 05:05:39 $
 */

public class TokenizerME implements Tokenizer {

  /**
   * The maximum entropy model to use to evaluate contexts.
   */
  private MaxentModel model;

  /**
   * The context generator.
   */
  private final ContextGenerator cg = new TokContextGenerator();

  private static final Double ONE = new Double(1.0);

  /** optimization flag to skip alpha numeric tokens for further
   * tokenization 
   */
  private boolean ALPHA_NUMERIC_OPTIMIZATION;

  /** list of probabilities for each token returned from call to
   * tokenize() */
  private List tokProbs;
  private List newTokens;

  /**
   * Class constructor which takes the string locations of the
   * information which the maxent model needs.
   */
  public TokenizerME(MaxentModel mod) {
    setAlphaNumericOptimization(false);
    model = mod;
    newTokens = new ArrayList();
    tokProbs = new ArrayList(50);
  }

  /** Returns the probabilities associated with the most recent
   * calls to tokenize() or tokenizePos().
   * @return probability for each token returned for the most recent
   * call to tokenize.  If not applicable an empty array is
   * returned.
   */
  public double[] getTokenProbabilities() {
    double[] tokProbArray = new double[tokProbs.size()];
    for (int i = 0; i < tokProbArray.length; i++) {
      tokProbArray[i] = ((Double) tokProbs.get(i)).doubleValue();
    }
    return tokProbArray;
  }

  /**
   * Tokenizes the string.
   *
   * @param d  The string to be tokenized.
   * @return   A span array containing individual tokens as elements.
   *           
   */
  public Span[] tokenizePos(String d) {
    List tokens = split(d);
    newTokens.clear();
    tokProbs.clear();
    /*
    if (1 == 1) {
      Span[] spans = new Span[tokens.size()];
      tokens.toArray(spans);
      for (int ti=0,tl=tokens.size();ti<tl;ti++) {
        tokProbs.add(ONE);
      }
      return spans;
    }
    */
    for (int i = 0, il = tokens.size(); i < il; i++) {
      Span s = (Span) tokens.get(i);
      String tok = d.substring(s.getStart(), s.getEnd());
      if (tok.length() < 2) {
        newTokens.add(s);
        tokProbs.add(ONE);
      }
      else if (useAlphaNumericOptimization() && PerlHelp.isAlphanumeric(tok)) {
        newTokens.add(s);
        tokProbs.add(ONE);
      }
      else {
        int start = s.getStart();
        int end = s.getEnd();
        final int origStart = s.getStart();
        double tokenProb = 1.0;
        for (int j = origStart + 1; j < end; j++) {
          double[] probs =
            model.eval(cg.getContext(new ObjectIntPair(tok, j - origStart)));
          String best = model.getBestOutcome(probs);
          //System.err.println("TokenizerME: "+tok.substring(0,j-origStart)+"^"+tok.substring(j-origStart)+" "+best+" "+probs[model.getIndex(best)]);
          tokenProb *= probs[model.getIndex(best)];
          if (best.equals(TokContextGenerator.SPLIT)) {
            newTokens.add(new Span(start, j));
            tokProbs.add(new Double(tokenProb));
            start = j;
            tokenProb = 1.0;
          }
        }
        newTokens.add(new Span(start, end));
        tokProbs.add(new Double(tokenProb));
      }
    }

    Span[] spans = new Span[newTokens.size()];
    newTokens.toArray(spans);
    return spans;
  }

  /**
   * Tokenize a String.
   *
   * @param s  The string to be tokenized.
   * @return   A string array containing individual tokens as elements.
   *           
   */
  public String[] tokenize(String s) {
    Span[] spans = tokenizePos(s);
    String[] toks = new String[spans.length];
    for (int ti = 0, tl = toks.length; ti < tl; ti++) {
      toks[ti] = s.substring(spans[ti].getStart(), spans[ti].getEnd());
    }
    return toks;
  }

  /** Constructs a list of Span objects, one for each whitespace
   * delimited token. Token strings can be constructed form these
   * spans as follows: d.substring(span.getStart(),span.getEnd());
   * @param d string to tokenize.
   * @return List is spans.
   **/
  static List split(String d) {
    int tokStart = -1;
    List tokens = new ArrayList();
    boolean inTok = false;

    //gather up potential tokens
    int end = d.length();
    for (int i = 0; i < end; i++) {
      if (Character.isWhitespace(d.charAt(i))) {
        if (inTok) {
          tokens.add(new Span(tokStart, i));
          inTok = false;
          tokStart = -1;
        }
      }
      else {
        if (!inTok) {
          tokStart = i;
          inTok = true;
        }
      }
    }
    if (inTok) {
      tokens.add(new Span(tokStart, end));
    }
    return (tokens);
  }

  public static void train(EventStream evc, File output) throws IOException {
    GISModel tokMod = GIS.trainModel(evc, 100, 10);
    new SuffixSensitiveGISModelWriter(tokMod, output).persist();
  }

  public static void train(String[] args) {
    try {
      FileReader datafr = new FileReader(new File(args[0]));
      File output = new File(args[1]);
      EventStream evc =
        new EventCollectorAsStream(new TokEventCollector(datafr));
      train(evc, output);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

  }

  /**
   * Used to have the tokenizer ignore tokens which only contain alpha-numeric characters.
   * @param opt set to true to use the optimization, false otherwise.
   */
  public void setAlphaNumericOptimization(boolean opt) {
    ALPHA_NUMERIC_OPTIMIZATION = opt;
  }

/**
 * Returns the value of the alpha-numeric optimization flag.
 * @return true if the tokenizer should use alpha-numeric optization, false otherwise.
 */
  public boolean useAlphaNumericOptimization() {
    return ALPHA_NUMERIC_OPTIMIZATION;
  }

  /**
   * Trains a new model. Call from the command line with "java
   * opennlp.tokenize.TokenizerME trainingdata modelname"
   */
  public static void main(String[] args) {
    TokenizerME.train(args);
  }

}