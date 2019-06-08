import socket, threading
from sklearn.cluster import KMeans
from sklearn.externals import joblib
import math
import numpy as np
import pandas as pd
import seaborn as sb
import matplotlib.pyplot as plt

x = 32
y = str(x) + '\n';
print(type(y))