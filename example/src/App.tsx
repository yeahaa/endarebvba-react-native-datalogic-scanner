import * as React from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';
import DatalogicScanner from 'react-native-datalogic-scanner';
import { useCallback, useEffect } from 'react';

export default function App() {
  const [result, setResult] = React.useState<string | undefined>();
  const [error, setError] = React.useState<any | undefined>();
  const [message, setMessage] = React.useState<string | undefined>();
  const [showCradle, setShowCradle] = React.useState<boolean>();

  let toggleButtons = () => {
    setShowCradle(!showCradle);
  };

  let clear = () => {
    setResult('');
    setError('');
  };

  let setBarcode = (barcode: string) => {
    setResult(barcode);
    setError('');
  };

  let setBarcodeError = (nativeError: any) => {
    setResult('');
    setError(nativeError.message);
  };

  let setErrorMessage = (nativeError: any) => {
    setMessage(nativeError.message);
  };

  let testScan = (success: boolean) => {
    clear();
    DatalogicScanner.testScan(success).then(setBarcode).catch(setBarcodeError);
  };

  let scanOnce = () => {
    clear();
    DatalogicScanner.scanOnce().then(setBarcode).catch(setBarcodeError);
  };

  let startScanning = () => {
    clear();
    DatalogicScanner.startScanning().then(setBarcode).catch(setBarcodeError);
  };

  let stopScanning = useCallback(() => {
    clear();
    DatalogicScanner.stopScanning();
  }, []);

  let testScanSuccess = () => testScan(true);

  let testScanFailure = () => testScan(false);

  let unlockFromCradle = () => {
    DatalogicScanner.unlockFromCradle()
      .then((unlocked: boolean) => {
        setMessage(unlocked ? 'Unlocked' : 'Already unlocked');
      })
      .catch(setErrorMessage);
  };

  let getCradleState = () => {
    DatalogicScanner.getCradleState().then(setMessage).catch(setErrorMessage);
  };

  let listenToCradle = () => {
    DatalogicScanner.listenToCradle();
  };

  useEffect(() => {
    const emitter = new NativeEventEmitter(NativeModules.DatalogicScanner);
    const listener = emitter.addListener('BarcodeScanned', (event) => {
      setBarcode(event.barcode);
    });

    return () => {
      stopScanning();
      listener.remove();
    };
  }, [stopScanning]);

  useEffect(() => {
    const emitter = new NativeEventEmitter(NativeModules.DatalogicScanner);
    const listener = emitter.addListener('CradleChanged', (event) => {
      setMessage(event.type);
    });

    return () => {
      listener.remove();
    };
  });

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.button} onPress={toggleButtons}>
        <Text style={styles.buttonText}>
          {showCradle ? 'Show scanning buttons' : 'Show cradle buttons'}
        </Text>
      </TouchableOpacity>
      {showCradle ? (
        <>
          <Text style={styles.text}>Message: {message}</Text>
          <TouchableOpacity
            style={[styles.button, styles.topMargin]}
            onPress={unlockFromCradle}
          >
            <Text style={styles.buttonText}>Unlock from cradle</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.topMargin]}
            onPress={getCradleState}
          >
            <Text style={styles.buttonText}>Get cradle state</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.topMargin]}
            onPress={listenToCradle}
          >
            <Text style={styles.buttonText}>Listen to cradle</Text>
          </TouchableOpacity>
        </>
      ) : (
        <>
          <Text style={styles.text}>Scan result: {result}</Text>
          <Text style={styles.text}>Error: {error}</Text>
          <TouchableOpacity
            style={[styles.button, styles.topMargin]}
            onPress={scanOnce}
          >
            <Text style={styles.buttonText}>Datalogic Scan Once</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.topMargin]}
            onPress={startScanning}
          >
            <Text style={styles.buttonText}>Datalogic Start Scanning</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.button} onPress={stopScanning}>
            <Text style={styles.buttonText}>Datalogic Stop Scanning</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.topMargin]}
            onPress={testScanSuccess}
          >
            <Text style={styles.buttonText}>TestScan Success</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.button} onPress={testScanFailure}>
            <Text style={styles.buttonText}>TestScan Failure</Text>
          </TouchableOpacity>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    minWidth: 288,
    fontSize: 20,
  },
  button: {
    backgroundColor: '#2196f3',
    paddingVertical: 8,
    paddingHorizontal: 16,
    margin: 4,
    minWidth: 192,
    borderRadius: 8,
  },
  buttonText: {
    fontSize: 20,
    color: '#ffffff',
    textAlign: 'center',
  },
  topMargin: {
    marginTop: 24,
  },
});
