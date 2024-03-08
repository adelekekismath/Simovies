#!/bin/bash
rm beans -rf
mkdir beans
javac -cp jars/* -s src/ -d beans/ src/supportGUI/*\.java src/algorithms/*\.java src/characteristics/*\.java src/terminator/*\.java
java -cp jars/*:beans/ supportGUI.Viewer
