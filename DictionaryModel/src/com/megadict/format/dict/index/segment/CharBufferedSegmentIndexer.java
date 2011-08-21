package com.megadict.format.dict.index.segment;

import static com.megadict.format.dict.index.segment.CharBufferTool.*;
import java.io.*;
import java.util.*;

import com.megadict.exception.*;
import com.megadict.format.dict.util.FileUtil;

public class CharBufferedSegmentIndexer extends BaseSegmentBuilder implements SegmentBuilder {

    public CharBufferedSegmentIndexer(File indexFile) {
        super(indexFile);
    }

    @Override
    public void build() {

        Reader reader = null;
        try {
            reader = makeReader();
            synchronized (coreBuffer) {
                performIndexingFileIntoSegments(reader);
            }            
        } catch (FileNotFoundException fnf) {
            throw new ResourceMissingException(indexFile());
        } catch (IOException ioe) {
            throw new OperationFailedException("reading index file", ioe);
        } finally {
            closeReader(reader);
        }
    }

    private Reader makeReader() throws FileNotFoundException {
        return FileUtil.newFileReader(indexFile());
    }

    private void closeReader(Reader reader) {
        FileUtil.closeReader(reader);
    }

    private void performIndexingFileIntoSegments(Reader reader) throws IOException {
        
        while (stillReceiveDataFrom(reader)) {
            
            if (firstBytesWasRead()) {
                createFirstPaddingBlock();
                continue;
            } else if (finalBytesWasRead()) {
                removeRedundantValuesBeforeProcess();
                readCharsThisTime = BUFFER_SIZE_IN_BYTES;
            }

            currentBlock = determineCurrentBlock();
            createAndStoreSegment();
            updatePreviousBlock(currentBlock);
        }

        processFinalBlock();
    }

    private boolean stillReceiveDataFrom(Reader reader) throws IOException {
        readCharsThisTime = reader.read(coreBuffer);
        return (readCharsThisTime == -1) ? false : countTotalBytesReadAndContinue();
    }

    private boolean countTotalBytesReadAndContinue() {
        totalCharsRead += readCharsThisTime;
        return true;
    }

    private boolean firstBytesWasRead() {
        return (totalCharsRead == readCharsThisTime);
    }

    private void createFirstPaddingBlock() {
        int headLeftOverLength = 0;
        int footerLeftOverLength = computeFooterLeftOverLength();
        String headword = extractHeadWordOfPaddingBlock();
        int offset = 0;
        Block block = new Block(headLeftOverLength, footerLeftOverLength, headword, offset);
        updatePreviousBlock(block);
    }

    private String extractHeadWordOfPaddingBlock() {
        int firstNewlineChar = findFirstNewlineChar(coreBuffer);
        int firstTabChar = findForwardFirstCharInRange(coreBuffer, 0, firstNewlineChar, '\t');
        char[] headword = copyOfRange(coreBuffer, 0, firstTabChar);
        return new String(headword);
    }

    private boolean finalBytesWasRead() {
        return readCharsThisTime < coreBuffer.length;
    }

    private void removeRedundantValuesBeforeProcess() {
        int startPositionToWipeOut = determineStartPositionToWipeOutRedundantValues();
        Arrays.fill(coreBuffer, startPositionToWipeOut, coreBuffer.length, '\0');
    }
    
    private int determineStartPositionToWipeOutRedundantValues() {
        int positionOfLastValue = readCharsThisTime - 1;
        return isNewlineChar(positionOfLastValue) ? positionOfLastValue - 1 : positionOfLastValue;
    }
    
    private boolean isNewlineChar(int position) {
        return coreBuffer[position] == (byte) '\n';
    }

    private Block determineCurrentBlock() {
        int headerLeftOverLength = computeHeadingLeftOverLength();
        int footerLeftOverLength = computeFooterLeftOverLength();
        String headword = extractBlockHeadingWord();        
        int offset = totalCharsRead - BUFFER_SIZE_IN_BYTES + headerLeftOverLength;        
        return new Block(headerLeftOverLength, footerLeftOverLength, headword, offset);
    }

    private int computeHeadingLeftOverLength() {
        int firstNewlineCharPos = findFirstNewlineChar(coreBuffer);
        return firstNewlineCharPos;
    }

    private int computeFooterLeftOverLength() {
        int lastNewlineCharPos = findLastNewlineChar(coreBuffer);
        return coreBuffer.length - lastNewlineCharPos;
    }

    private String extractBlockHeadingWord() {
        int firstNewlineChar = findFirstNewlineChar(coreBuffer);

        int nextTabChar = findForwardFirstCharInRange(coreBuffer, firstNewlineChar,
                coreBuffer.length, '\t');

        char[] headword = copyOfRange(coreBuffer, firstNewlineChar + 1, nextTabChar);

        return new String(headword);
    }

    private void createAndStoreSegment() {
        Segment newSegment = createSegmentWithBuiltBlock();
        storeCreatedSegment(newSegment);
    }

    private Segment createSegmentWithBuiltBlock() {
        String lowerbound = previousBlock.headword;
        String upperbound = currentBlock.headword;
        int offset = previousBlock.offset;
        int length = computeSegmentLength();

        return new Segment(lowerbound, upperbound, offset, length);
    }

    private int computeSegmentLength() {
        int excludedPreviousHeaderLeftOver = readCharsThisTime - previousBlock.headerLeftOverLength;
        int includedCurrentBlock = excludedPreviousHeaderLeftOver + currentBlock.headerLeftOverLength;
        return includedCurrentBlock;
    }

    private void updatePreviousBlock(Block currentBlock) {
        previousBlock = currentBlock;
    }

    private void processFinalBlock() {
        readCharsThisTime = determineLastByteRead();
        currentBlock = createTrailingBlock();
        createAndStoreSegment();
    }

    private Block createTrailingBlock() {
        int headerLeftOverLenght = 0;
        int footerLeftOverLength = 0;
        String headword = extractLastWordOfFinalBlock();
        int offset = 0;

        return new Block(headerLeftOverLenght, footerLeftOverLength, headword, offset);
    }

    private String extractLastWordOfFinalBlock() {
        int lastNewlineChar = findLastNewlineCharOfBlock();
        int nextTabChar = findNextTabChar(lastNewlineChar);

        char[] headword = copyContent(lastNewlineChar + 1, nextTabChar);

        return new String(headword);
    }

    private int findLastNewlineCharOfBlock() {
        int start = readCharsThisTime - 1;
        int end = 0;
        return findBackwardFirstCharInRange(coreBuffer, start, end, '\n');
    }

    private int determineLastByteRead() {
        return totalCharsRead % BUFFER_SIZE_IN_BYTES;
    }

    private int findNextTabChar(int lastNewlineChar) {
        int start = lastNewlineChar;
        int end = coreBuffer.length;
        return findForwardFirstCharInRange(coreBuffer, start, end, '\t');
    }

    private char[] copyContent(int start, int end) {
        return copyOfRange(coreBuffer, start, end);
    }

    private static final int BUFFER_SIZE_IN_BYTES = FileUtil.DEFAULT_BUFFER_SIZE_IN_BYTES;
    private static final char[] coreBuffer = new char[BUFFER_SIZE_IN_BYTES];

    private Block previousBlock;
    private Block currentBlock;

    private int totalCharsRead = 0;
    private int readCharsThisTime = 0;
}
