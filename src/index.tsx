import { NativeModules } from 'react-native';

type DatalogicScannerType = {
  multiply(a: number, b: number): Promise<number>;
};

const { DatalogicScanner } = NativeModules;

export default DatalogicScanner as DatalogicScannerType;
