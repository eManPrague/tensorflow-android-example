# Simple Android app example with Tensorlfow Lite use

Convolutional Neural Network are current state of the art for image classification. You can find here how to implement a frozen `.tflite` model to an Android app.


## Application
The Convolutional Neural Network recognizes a dug toy. Application takes frames from camera and passes it to trained Tensorflow model for recognition.

<img src="app.gif" width="320">


## Note
- This repo is part of our blog post [eMan Tensorflow](https://www.eman.cz/blog/trenujeme-ai-tensorflow-konvolucne-neuronove-siete-praxi-appke/)
- If you're interested in training your own model and how to freeze it for an Android app, check our repository [Simple Convolutional Neural Network with Tensorflow](https://github.com/eManPrague/tensorflow-python-example)
- Built with help of [Camera2 API example](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/contrib/lite/examples) and [Tensorflow Lite example](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/contrib/lite/examples)
- If you find this repository useful, please consider ★ starring it
