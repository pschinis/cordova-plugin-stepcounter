/*
    Copyright 2015 Jarrod Linahan <jarrod@texh.net>

    Permission is hereby granted, free of charge, to any person obtaining
    a copy of this software and associated documentation files (the
    "Software"), to deal in the Software without restriction, including
    without limitation the rights to use, copy, modify, merge, publish,
    distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to
    the following conditions:

    The above copyright notice and this permission notice shall be
    included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
    EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
    MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
    NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
    LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
    OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
    WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

*/

module.exports = {

    start: function (postUrl, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "StepCounterPlugin", "start", []);
    },
 
    stop: function ( successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "StepCounterPlugin", "stop", []);
    },

    read: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "StepCounterPlugin", "read", []);
    },
 
    hasMinPlayServicesVersion: function (successCallback, errorCallback) {
        cordova.exec(function(res) {successCallback(!!res);}, errorCallback, "StepCounterPlugin", "hasMinPlayServicesVersion", []);
    }
};
